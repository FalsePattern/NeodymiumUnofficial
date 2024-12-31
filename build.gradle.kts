plugins {
    id("fpgradle-minecraft") version ("0.10.0")
}

group = "makamys"

minecraft_fp {
    mod {
        modid = "neodymium"
        name = "Neodymium Unofficial"
        rootPkg = "$group.neodymium"
    }

    mixin {
        pkg = "mixin"
        pluginClass = "MixinConfigPlugin"
    }

    core {
        accessTransformerFile = "neodymium_at.cfg"
    }

    tokens {
        tokenClass = "Tags"
    }

    publish {
        changelog = "https://github.com/FalsePattern/NeodymiumUnofficial/tag/$version"

        maven {
            repoUrl = "https://mvn.falsepattern.com/releases/"
            repoName = "mavenpattern"
        }

        curseforge {
            projectId = "1031274"
        }

        modrinth {
            projectId = "YRBOv8ax"
        }
    }
}

repositories {
    exclusive(mavenpattern(), "com.falsepattern")
    exclusive(maven("mega_uploads", "https://mvn.falsepattern.com/gtmega_uploads"), "optifine")
}

dependencies {
    compileOnly("com.falsepattern:rple-mc1.7.10:1.5.0:dev")
    compileOnly("com.falsepattern:falsetweaks-mc1.7.10:3.8.3:dev")

    compileOnly("optifine:optifine:1.7.10_hd_u_e7:dev")
}