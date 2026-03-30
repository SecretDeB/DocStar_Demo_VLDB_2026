Instruction File:

# Prerequisites
Before starting, ensure you have the following installed on your machine:

* Java Development Kit (JDK): JDK 17 or higher is recommended
* Node.js




Open 4 separate terminal
Terminal 1: Start frontend

`
cd DocStar_Demo_VLDB_2026/document_react

npm i

num run dev `

Terminal 2: Start middleware

`cd DocStar_Demo_VLDB_2026/document_middleware/middleware/

mvn clean install

cd target

java -jar -Xms1g -Xmx16g ./docstar_middleware.jar `

Terminal 3: Start server

`cd DocStar_Demo_VLDB_2026/document_java_server/server/

mvn clean install

cd target

java -jar -Xms1g -Xmx16g ./docstar_server.jar 1 `

Terminal 4: Start server

` cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 2 `

Terminal 5: Start server

` cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 3 `

Terminal 6: Start server

` cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 4 `

Open browser:
Login as DBO/client:
 ` http://localhost:5174/ `








### Video link: https://youtu.be/v6k9_WTE1P0
