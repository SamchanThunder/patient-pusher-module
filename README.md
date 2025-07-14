# patient-listener-module

OpenMRS Module that listens for patient creation/registration then posts/pushes it to a OpenHIM FHIR server.

If you want to make changes, the main files to be edited are PatientPusher.java and PatientPusherActivator.java both in ./patientpush/api/src/main/java/org/openmrs/module/patientpush

The module file (.omod) is in ./patientpush/omod/target/
To rebuild module, CD to patientpush folder and in terminal type mvn clean install