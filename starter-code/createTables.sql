CREATE TABLE Users (
    username VARCHAR(20) PRIMARY KEY,
    password VARCHAR(20),
    balance int,
    CHECK (balance > -1)
);

CREATE TABLE Reservations (
    fid_1 INT REFERENCES Flights(fid),
    fid_2 INT,
    day_of_month INT,
    paid INT,                                   -- 0 or 1, for no / yes paid
    canceled INT,                               -- 1 for canceled, 0 for not canceled
    ID INT PRIMARY KEY,
    username VARCHAR(20) REFERENCES Users(username)
);