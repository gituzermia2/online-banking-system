# online-banking-system
Java + SQL based Online Banking System backend
# Online Banking System (Java + SQL)

This project is a simple backend implementation of an **Online Banking System** using  
**Java, JDBC, and an SQL database (H2 Embedded DB).**

It includes:
- User management  
- Account creation  
- Secure money transfers (ACID-compliant)  
- Transaction logs  
- Audit logging  
- Automatic schema creation

---

## 🚀 Features

### **For Customers**
✔ View accounts  
✔ View balances  
✔ Transfer funds internally  
✔ Transaction history  

### **For Admin (backend console)**
✔ Create users  
✔ Create accounts  
✔ Monitor transactions  
✔ Review audit logs  

---

## 📦 Project Structure
online-banking-system/
│
├── src/main/java/BankApp.java
├── pom.xml
└── README.md

---

## 🛠 Requirements

- Java (JDK 8+)
- Maven (optional but recommended)
- No external SQL server required — uses **H2 embedded file DB**

---

## ▶️ How to Run

### **Option 1: Run with Maven**
mvn compile
mvn exec:java -Dexec.mainClass="BankApp"

### **Option 2: Run with `javac`**
Download H2 JAR:

https://www.h2database.com/

Then:
javac -cp h2.jar BankApp.java
java -cp .:h2.jar BankApp

(Use `;` instead of `:` on Windows)

---

## 🧪 What the Program Demonstrates

- Creates users (Alice, Bob)
- Creates accounts for each user
- Shows account balances before/after transfer
- Performs a secure transfer with:
  - Row locking  
  - Transaction commit/rollback  
- Shows transaction logs  
- Shows audit logs  

---

## 🧰 Database
H2 auto-creates this file:
bankdb.mv.db

Located in the project root.

---

## 📜 License
MIT License

---

## 👤 Author
Malav Madnani  
B.Tech CSE (AI & ML)
