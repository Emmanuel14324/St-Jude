package ug.sjch.security;

public final class PasswordHashGenerator {
    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.out.println("Usage: PasswordHashGenerator <password>");
            return;
        }
        System.out.println(PasswordHasher.hash(args[0]));
    }
}
