package java.net;

import sun.security.action.GetPropertyAction;

/**
 *
 *
 */
public abstract class InetLookupPolicy {
    /**
     * Returns the address family type to lookup for
     * @return address family type
     */
    public abstract AddressFamily getAddressesFamily();

    /**
     * Returns the order in which addresses should be returned from the name service
     * @return addresses order
     */
    public abstract AddressesOrder getAddressesOrder();

    /**
     * Type which specifies the requested address family
     */
    public enum AddressFamily {
        /**
         * Unspecified address family. Instructs {@code NameService} to return network addresses
         * for both address families: IPv4 and IPv6.
         */
        ANY,

        /**
         * Lookup IPv4 addresses only
         */
        INET,

        /**
         * Lookup IPv6 addresses only
         */
        INET6
    }

    /**
     * Type which specifies the requested order of addresses
     */
    public enum AddressesOrder {
        /**
         * The addresses are order in the same way as returned by the name service provider.
         */
        SYSTEM,

        /**
         * IPv4 addresses are preferred over IPv6 addresses and returned first.
         */
        IPV4_FIRST,

        /**
         * IPv6 addresses are preferred over IPv4 addresses and returned first.
         */
        IPV6_FIRST
    }


    /**
     * System-wide {@code InetLookupPolicy} initialized from {@code "java.net.preferIPv4Stack"} and
     * {@code "java.net.preferIPv6Addresses"} system property values.
     **/
    static final InetLookupPolicy PLATFORM = new PlatformInetLookupPolicy();

    private final static class PlatformInetLookupPolicy extends InetLookupPolicy {
        private final AddressesOrder order;
        private final AddressFamily family;
        private static final String PREFER_IPV4_VALUE;
        private static final String PREFER_IPV6_VALUE;

        static {
            PREFER_IPV4_VALUE = GetPropertyAction.privilegedGetProperty("java.net.preferIPv4Stack");
            PREFER_IPV6_VALUE = GetPropertyAction.privilegedGetProperty("java.net.preferIPv6Addresses");
        }

        @Override
        public AddressFamily getAddressesFamily() {
            return family;
        }

        @Override
        public AddressesOrder getAddressesOrder() {
            return order;
        }

        private PlatformInetLookupPolicy() {
            family = initializeFamily();
            order = initializeOrder();
        }

        // Initialize the addresses family field. The following information is used
        // to make a decision about the supported address families:
        //     a) java.net.preferIPv4Stack system property value
        //     b) OS configuration which checked via ipv4_available call
        //     c) Type of InetAddress.impl instance
        private static AddressFamily initializeFamily() {
            boolean ipv4Available = isIPv4Available();
            if ("true".equals(PREFER_IPV4_VALUE) && ipv4Available) {
                return AddressFamily.INET;
            }
            // Check if IPv6 is not supported
            if (InetAddress.impl instanceof Inet4AddressImpl) {
                return AddressFamily.INET;
            }
            // Check if system supports IPv4, if not return IPv6
            if (!ipv4Available) {
                return AddressFamily.INET6;
            }
            return AddressFamily.ANY;
        }

        // Initialize the order of addresses
        private AddressesOrder initializeOrder() {
            return switch (family) {
                case INET, INET6 -> AddressesOrder.SYSTEM;
                case ANY -> getOrderForAnyAddressFamily();
            };
        }

        // Initializes the order of addresses if ANY address family is selected.
        // Order is initialized from the value of system properties.
        private static AddressesOrder getOrderForAnyAddressFamily() {
            // Logic here is identical to the initialization in InetAddress static initializer
            if (PREFER_IPV6_VALUE == null) {
                return AddressesOrder.IPV4_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("true")) {
                return AddressesOrder.IPV6_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("false")) {
                return AddressesOrder.IPV4_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("system")) {
                return AddressesOrder.SYSTEM;
            } else {
                return AddressesOrder.IPV4_FIRST;
            }
        }
    }

    private static native boolean isIPv4Available();
}
