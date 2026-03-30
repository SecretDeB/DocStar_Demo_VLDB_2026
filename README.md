# DocStar Demo

## Prerequisites
Before starting, ensure you have the following installed on your machine:

## Java Development Kit (JDK)

Ensure you have Java installed (JDK 17 or higher is recommended).

Verify installation by opening a terminal and typing:

java -version


## Node.js

For Node.js, go to https://nodejs.org/dist/v25.2.1/node-v25.2.1-x64.msi and run the installer,

Download and run the installer: Node.js v25.2.1 Installer

Verify installation by typing:

node -v
npm -v

## MySQL Server
You can find the download at https://dev.mysql.com/downloads/mysql/8.0.html.

If you already have it installed, replace the root password inside application.properties at path document_middleware/middleware/src/main/resources/application.properties and change the property spring.datasource.password=YOUR_MYSQL_ROOT_PASSWORD before running the middleware.

After installing MySQL, open a terminal

mysql -u username -p 

Enter the password

Run source document_middleware/INITIALIZE.sql to create the database tables.


# Installation & Setup

## 1. Clone Repository

Clone the repository using "git clone https://github.com/SecretDeB/DocStar_Demo_VLDB_2026.git".

## 2. Backend Setup (Java)

The backend requires running 4 distributed servers and the middleware Spring Boot Application.

### Steps to start the Backend Servers

1. Open a terminal in document_java_server/server.
   
2. Type the command "mvn clean install", to generate the jar file.
   
3. Now we need to go into the target folder by doing "cd target".
   
4. Open 3 additional terminals in the same directory (total 4 terminals).
   
5. Run the distributed servers in order using the following commands:

Terminal 1:

```java -jar -Xms1g -Xmx16g ./docstar_server.jar 1```

Terminal 2:

```java -jar -Xms1g -Xmx16g ./docstar_server.jar 2```

Terminal 3:

```java -jar -Xms1g -Xmx16g ./docstar_server.jar 3```

Terminal 4:

```java -jar -Xms1g -Xmx16g ./docstar_server.jar 4```

## 3. Middleware Setup (Java)

### Steps to start the Backend Servers

1. Open a terminal in document_middleware/middleware.

2. Run ```mvn clean install -DskipTests``` to create a jar file.

3. Then move into target folder by running ```cd target```.

4. Now we can start the middleware server by running ```java -jar -Xms1g -Xmx16g ./docstar_middleware.jar```.


## 4. Frontend Setup (React)

1. Open a terminal in document_react.

2. Install required packages using npm install, and wait until it finishes.

3. Now we can start the development server using ```npm run dev```

Once started, open your browser and navigate to: ```http://localhost:5173```


🔑 Login Credentials
Make sure you set up the database as mentioned in the beginning.

Use the following credentials to access the system.

Admin Access
Username: alice.admin
Password: alice

Client Access
Username: alice.user
Password: alice

Note: User credentials and roles can be modified by editing the admins.csv and client.csv files located in the src/main/resources folder of the Java project.

🛠️ Troubleshooting
Port Conflicts: Ensure ports 8080 (Spring Boot), 5173 (React), and the ports used by the 4 servers (typically defined in server.properties) are free before starting.

Connection Refused: If the frontend cannot fetch data, ensure DocumentmsApplication is running and there are no errors in the Java console.

npm Error: If npm install fails, try deleting the node_modules folder and package-lock.json, then run npm install again.
=======
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
>>>>>>> 4d92ec5327726aae637348b87787bf270935a2aa
