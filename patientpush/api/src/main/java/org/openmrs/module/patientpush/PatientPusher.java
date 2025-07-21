// Checks when patient is created in OpenMRS then fetches it and posts it to FHIR Server.
package org.openmrs.module.patientpush;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;
import javax.jms.Message;
import javax.jms.MapMessage;
import java.util.Map;
import java.util.List;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PatientPusher implements EventListener {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	// Servers (Respective to my Local Environment)
	private String urlMRS = "http://openmrs:8080/openmrs/ws/fhir2/R4/Patient/";
	
	private String urlHIM = "http://openhim-core:5001/fhir/Patient";
	
	// OpenMRS Auth Credentials (Must be hidden in production)
	private String usernameMRS = "admin";
	
	private String passwordMRS = "Admin123";
	
	// OpenHIM Auth Credentials (Must be hidden in production)
	private String usernameHIM = "interop-client";
	
	private String passwordHIM = "interop-password";
	
	@Override
	public void onMessage(Message message) {
        String uuid = null;
		try {
            MapMessage mapMessage = (MapMessage) message; // Changes message to Key Value pairs.
            String action = mapMessage.getString("action"); // Checks the value of the action key. What event happened (CREATE, UPDATE, etc)?
            
            // Checks if action was a create. Future Consideration: Implement update patient
            // The Activator file only allows CREATED actions to here, but this is a double check.
            if (Event.Action.CREATED.toString().equals(action)) {
                log.info("Patient push: PATIENT CREATED");
                uuid = mapMessage.getString("uuid"); // Gets value of uuid key

                // Fetch Patient Data from OpenMRS
                URL getUrl = new URL(urlMRS + uuid);
                log.info("Patient push: GOT URL: " + urlMRS + uuid);

                HttpURLConnection getCon = (HttpURLConnection) getUrl.openConnection(); // Opens connection to URL
                log.info("Patient push: OPEN URL SUCCESSFUL");

                getCon.setRequestMethod("GET");

                String authMRS = usernameMRS + ":" + passwordMRS;
                String encodedAuthMRS = Base64.getEncoder().encodeToString(authMRS.getBytes(StandardCharsets.UTF_8)); // Required format for auth
                getCon.setRequestProperty("Authorization", "Basic " + encodedAuthMRS);
                getCon.setRequestProperty("Accept", "application/json"); // Make server return Json format

                String patientJson = new Scanner(getCon.getInputStream(), "UTF-8").useDelimiter("\\A").next(); // Converts json to string

                log.info("Fetched patient JSON: " + patientJson);
                
                // Add a system[0].identifier to the json
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> jsonMap = mapper.readValue(patientJson, Map.class);

                List<Map<String, Object>> identifiers = (List<Map<String, Object>>) jsonMap.get("identifier");
                if (identifiers != null && identifiers.size() > 0) {
                    identifiers.get(0).put("system", "openmrs");
                }

                patientJson = mapper.writeValueAsString(jsonMap);


                // Post Data to OpenHIM FHIR Server
                URL postUrl = new URL(urlHIM);
                HttpURLConnection postCon = (HttpURLConnection) postUrl.openConnection(); // Opens connection to URRL
                postCon.setRequestMethod("POST");
                postCon.setDoOutput(true); // We want to write data with a request body
                postCon.setRequestProperty("Content-Type", "application/json");

                String authHIM = usernameHIM + ":" + passwordHIM;
                String encodedAuthHIM = Base64.getEncoder().encodeToString(authHIM.getBytes(StandardCharsets.UTF_8));
                postCon.setRequestProperty("Authorization", "Basic " + encodedAuthHIM);

                // Posts patientJson
                try (OutputStream os = postCon.getOutputStream()) {
                    byte[] input = patientJson.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = postCon.getResponseCode();

                getCon.disconnect();
                postCon.disconnect();
            }
        }
		catch (Exception e) {
			 log.error("Failed to push patient to FHIR server. UUID: " + uuid, e);
		}
	}
}
