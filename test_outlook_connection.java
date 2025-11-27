import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class test_outlook_connection {
    public static void main(String[] args) {
        try {
            System.out.println("Testing Microsoft Graph API access with valid token...");
            
            // Read token from file
            String userHome = System.getProperty("user.home");
            String tokenPath = userHome + "\\.todo-desktop\\outlook_token.json";
            System.out.println("Reading token from: " + tokenPath);
            
            String tokenContent = java.nio.file.Files.readString(java.nio.file.Paths.get(tokenPath));
            JsonObject tokenJson = JsonParser.parseString(tokenContent).getAsJsonObject();
            String accessToken = tokenJson.get("accessToken").getAsString();
            
            System.out.println("Token extracted successfully (length: " + accessToken.length() + ")");
            
            // Test API call
            HttpClient client = HttpClient.newHttpClient();
            String url = "https://graph.microsoft.com/v1.0/me/messages?$top=5&$select=id,subject,from,receivedDateTime";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
            
            System.out.println("Making request to: " + url);
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("Response status: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                int messageCount = responseJson.getAsJsonArray("value").size();
                System.out.println("SUCCESS: Retrieved " + messageCount + " emails");
            } else {
                System.out.println("ERROR: " + response.body());
            }
            
        } catch (Exception e) {
            System.out.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
