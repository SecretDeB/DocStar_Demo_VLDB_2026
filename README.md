📋 Prerequisites
Before starting, ensure you have the following installed on your machine:

Java Development Kit (JDK)

Ensure you have Java installed (JDK 17 or higher is recommended).

Verify installation by opening a terminal and typing:

java -version
Node.js

For Node.js, go to https://nodejs.org/dist/v25.2.1/node-v25.2.1-x64.msi and run the installer,

Download and run the installer: Node.js v25.2.1 Installer

Verify installation by typing:

node -v
npm -v

We also need to install MySQL Server

If you already have it installed replace the root password inside application.properties at path document_middleware/middleware/src/main/resources/application.properties 
and change the property spring.datasource.password=YOUR_MYSQL_ROOT_PASSWORD


After installing MySQL, open a terminal

mysql -u username -p 

Enter the password

source document_middleware/INITIALIZE.sql


🚀 Installation & Setup

Step 1: Clone Repository

Clone the repository using git clone https://github.com/SecretDeB/DocStar_Demo_VLDB_2026.git

Step 2: Backend Setup (Java)

The backend requires running 4 distributed servers and the Main Spring Boot Application.

Run these commands

cd document_java_server/server

mvn clean install 

cd target

Open 4 separate terminals.

Run the distributed servers in order using the following commands:

Terminal 1:
java -jar -Xms1g -Xmx16g ./docstar_server.jar 1

Terminal 2:
java -jar -Xms1g -Xmx16g ./docstar_server.jar 2

Terminal 3:
java -jar -Xms1g -Xmx16g ./docstar_server.jar 3

Terminal 4:
java -jar -Xms1g -Xmx16g ./docstar_server.jar 4


Step 3: Middleware Setup (Java)

Run these commands

cd document_middleware/middleware

mvn clean install -DskipTests

cd target


java -jar -Xms1g -Xmx16g ./docstar_middleware.jar


Step 4: Frontend Setup (React)

cd into document_react

Install packages:
npm install

Start the development server:
npm run dev

Once started, open your browser and navigate to: http://localhost:5173


🔑 Login Credentials
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