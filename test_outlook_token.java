import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class test_outlook_token {
    public static void main(String[] args) {
        String userHome = System.getProperty("user.home");
        Path tokenPath = Paths.get(userHome, ".todo-desktop", "outlook_token.json");
        
        System.out.println("Checking token file at: " + tokenPath);
        
        if (Files.exists(tokenPath)) {
            System.out.println("Token file exists");
            try {
                String content = Files.readString(tokenPath);
                System.out.println("Token file content: " + content);
            } catch (Exception e) {
                System.out.println("Error reading token file: " + e.getMessage());
            }
        } else {
            System.out.println("Token file does NOT exist");
        }
        
        System.out.println("Current time: " + Instant.now());
    }
}
