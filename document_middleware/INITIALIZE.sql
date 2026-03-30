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

insert into admin values(1, '123 Main Street, New York, NY', 'vs787@njit.edu', 'Kris Sreeramakavacham', 'cyrus.com', '1234567890', 'kris.admin');

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

insert into client values(1, "123 Main Street, New York, NY","svkrishna6342@gmail.com","Kris Sreeramakavacham","cyrus.com", "1234567890","kris.user");
insert into client values(2, "123 Main Street, New York, NY","shantanu.sharma@njit.edu","Shantanu","shantanu", "1234567890","shantanu");
insert into client values(3, "123 Main Street, New York, NY","kk675@njit.edu","Komal","komal", "1234567890","komal");

select * from admin;
select * from client;