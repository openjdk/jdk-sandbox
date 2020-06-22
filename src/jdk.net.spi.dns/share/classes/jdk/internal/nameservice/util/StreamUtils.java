package jdk.internal.nameservice.util;

import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {

    public static <T> Stream<T> ensureStreamHasAnyElement(Stream<T> originalStream, String message) throws UnknownHostException {
        Iterator<T> itr = originalStream.iterator();
        if (!itr.hasNext()) {
            throw new UnknownHostException(message);
        }
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(itr,0), false);
    }

}
