create database docstar;

use docstar;

CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

insert into admin values(1, '123 Main Street, New York, NY', 'alice.admin@gmail.com', 'Alice', 'alice', '1234567890', 'alice.admin');

CREATE TABLE `client` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `document` (
  `id` bigint NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `upload_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

insert into client values(1, "123 Main Street, New York, NY","alice@gmail.com","Alice","alice", "1234567890","alice.user");
insert into client values(2, "123 Main Street, New York, NY","bob@gmail.com","bob","bob", "1234567890","bob.user");
insert into client values(3, "123 Main Street, New York, NY","steve@gmail.com","Steve","steve", "1234567890","steve.user");
select * from admin;
select * from client;