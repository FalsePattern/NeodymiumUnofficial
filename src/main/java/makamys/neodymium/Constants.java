package makamys.neodymium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Constants {
    
    public static final String MODID = Tags.MOD_ID;
    public static final String VERSION = Tags.MOD_VERSION;
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    
    public static final String PROTOCOL = "neodymiumvirtualjar";
    
    public static final boolean KEEP_RENDER_LIST_LOGIC = Boolean.parseBoolean(System.getProperty("neodymium.keepRenderListLogic", "false"));
    
}
