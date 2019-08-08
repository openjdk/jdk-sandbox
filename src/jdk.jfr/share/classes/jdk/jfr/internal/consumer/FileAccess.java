package jdk.jfr.internal.consumer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Protected by modular boundaries.
public abstract class FileAccess {
    public final static FileAccess PRIVILIGED = new UnPriviliged();
    // TODO: Should be changed Priviliged class
    public final static FileAccess UNPRIVILIGED = new UnPriviliged();

    abstract RandomAccessFile openRAF(File f, String mode) throws FileNotFoundException;
    abstract DirectoryStream<Path> newDirectoryStream(Path repository) throws IOException;

    static class Priviliged extends FileAccess {
        @Override
        RandomAccessFile openRAF(File f, String mode) {
            // TDOO: Implement
            return null;
        }

        @Override
        protected DirectoryStream<Path> newDirectoryStream(Path repository) {
            // TDOO: Implement
            return null;
        }
    }

    static class UnPriviliged extends FileAccess {
        @Override
        RandomAccessFile openRAF(File f, String mode) throws FileNotFoundException {
            return new RandomAccessFile(f, mode);
        }

        @Override
        DirectoryStream<Path> newDirectoryStream(Path dir) throws IOException {
            return Files.newDirectoryStream(dir);
        }

    }
}
