#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
#ifdef RPLE
in vec2 BTexCoordR;
in vec2 BTexCoordG;
in vec2 BTexCoordB;
#else
in vec2 BTexCoord;
#endif
#ifdef DYN_LIGHTS
in vec3 UPos;
in float PlayerLight;
#endif
in vec4 Color;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;
in float FogFactor;

#ifdef DYN_LIGHTS
uniform DYN_LIGHTS_DATATYPE playerHandLight;
#endif
uniform sampler2D atlas;
#ifdef RPLE
uniform sampler2D lightTexR;
uniform sampler2D lightTexG;
uniform sampler2D lightTexB;
#else
uniform sampler2D lightTex;
#endif

#ifdef DYN_LIGHTS
DYN_LIGHTS_DATATYPE computeDynamicLights()
{
    float light;
    #ifdef DYN_LIGHTS_CIRCULAR
    light = length(UPos) / 16.0;
    #else
    vec3 pos = abs(UPos);
    float len = max(pos.x - 0.5, 0) + max(pos.y - 0.5, 0) + max(pos.z - 0.5, 0);
    light = len / 16.0;
    #endif
    return clamp(playerHandLight - light, 0.0, 1.0);
}

vec2 dynLightMix(vec2 texCoord, float blockLight) {
    return max(texCoord, vec2(blockLight, 0));
}
#endif

void main()
{
    vec2 texCoord = TexCoord;
    #ifdef SHORT_UV
        texCoord /= 32768.0;
    #endif
    vec4 texColor = texture(atlas, texCoord);

    vec4 colorMult = Color / 256.0;

    vec4 lightyColor;
    #ifdef RPLE
        // RPLE assumes that we're using the legacy opengl pipeline, so it creates 3 textures:
        //  color dark       bright
        //   RED: Cyan    -> White
        // GREEN: Magenta -> White
        //  BLUE: Yellow  -> White
        // In each texture, only a single channel varies, while the other 2 are set to 1, so the result becomes:
        // (r, 1, 1) * (1, g, 1) * (1, 1, b) = (r, g, b)
        vec2 lightTexCoordR = (BTexCoordR + 32767) / 65535.0;
        vec2 lightTexCoordG = (BTexCoordG + 32767) / 65535.0;
        vec2 lightTexCoordB = (BTexCoordB + 32767) / 65535.0;
        #ifdef DYN_LIGHTS
            vec3 dynLights = computeDynamicLights();
            lightTexCoordR = dynLightMix(lightTexCoordR, dynLights.r);
            lightTexCoordG = dynLightMix(lightTexCoordG, dynLights.g);
            lightTexCoordB = dynLightMix(lightTexCoordB, dynLights.b);
        #endif
        lightyColor =
        texture(lightTexR, lightTexCoordR) *
        texture(lightTexG, lightTexCoordG) *
        texture(lightTexB, lightTexCoordB);
    #else
        vec2 lightTexCoord = (BTexCoord + 8.0) / 256.0;
        #ifdef DYN_LIGHTS
            lightTexCoord = dynLightMix(lightTexCoord, computeDynamicLights());
        #endif
        lightyColor = texture(lightTex, lightTexCoord);
    #endif
    vec4 rasterColor;
    #ifdef PASS_0
        rasterColor = vec4((texColor.xyz * colorMult.xyz) * lightyColor.xyz, texColor.w);
    #else
        rasterColor = (texColor * colorMult) * lightyColor;
    #endif

    #ifdef RENDER_FOG
        FragColor = vec4(mix(FogColor.xyz, rasterColor.xyz, FogFactor), rasterColor.w);
    #else
        FragColor = rasterColor;
    #endif
}
