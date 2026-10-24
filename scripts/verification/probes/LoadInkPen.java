/** Load/link probe only: does not open a pen window, capture input or activate a session. */
public final class LoadInkPen {
    public static void main(String[] arguments) {
        if (arguments.length != 1) throw new IllegalArgumentException("Expected one absolute DLL path");
        String architecture = System.getProperty("os.arch");
        if (!architecture.equals("amd64") && !architecture.equals("x86_64")) {
            throw new IllegalStateException("This probe requires an x86_64 JVM");
        }
        System.load(java.nio.file.Path.of(arguments[0]).toAbsolutePath().toString());
        System.out.println("NATIVE_LIBRARY_LOADED x86_64");
    }
}
