import sun.hotspot.WhiteBox;

public final class Settings {

    public String reclaimPolicy = WhiteBox.getWhiteBox().getStringVMFlag("MetaspaceReclaimPolicy");
    public boolean usesAllocationGuards = WhiteBox.getWhiteBox().getBooleanVMFlag("MetaspaceGuardAllocations");

    final public boolean doesReclaim() {
        return reclaimPolicy.equals("balanced") || reclaimPolicy.equals("aggessive");
    }

    final static long rootChunkWordSize = 512 * 1024;

    static Settings theSettings;

    static Settings settings()  {
       if (theSettings == null) {
            theSettings = new Settings();
       }
       return theSettings;
    }

}
