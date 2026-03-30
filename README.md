Instruction File:

# Prerequisites
Before starting, ensure you have the following installed on your machine:

* Java Development Kit (JDK): JDK 17 or higher is recommended
* Node.js




Open six separate terminals

In Terminal 1: Start frontend

```bash
cd DocStar_Demo_VLDB_2026/document_react
npm i
num run dev
```

In Terminal 2: Start middleware

```bash
cd DocStar_Demo_VLDB_2026/document_middleware/middleware/
mvn clean install
cd target
java -jar -Xms1g -Xmx16g ./docstar_middleware.jar
```

In Terminal 3: Start server 1

```bash
cd DocStar_Demo_VLDB_2026/document_java_server/server/
mvn clean install
cd target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 1
```

In Terminal 4: Start server 2

```
cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 2
```

In Terminal 5: Start server 3

```bash
cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 3
```

In Terminal 6: Start server 4

```bash
cd DocStar_Demo_VLDB_2026/document_java_server/server/target
java -jar -Xms1g -Xmx16g ./docstar_server.jar 4
```

Now, open your browser. The interface should be accessible at `http://localhost:5173/`.

Log in as DBO/client to access the interface.








### Video link: https://youtu.be/v6k9_WTE1P0
