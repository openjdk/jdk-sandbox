package javax.management;

/**
 * Listener interface to get notified when MBeanServer
 * is created/released
 */
public interface MBeanServerFactoryListener {
    /**
     * When MbeanServer is added
     * @param mBeanServer the mBeanServer
     */
    public void onMBeanServerCreated(MBeanServer mBeanServer);

    /**
     * When MBeanServer is released
     * @param mBeanServer the mBeanServer
     */
    public void onMBeanServerRemoved(MBeanServer mBeanServer);
}
