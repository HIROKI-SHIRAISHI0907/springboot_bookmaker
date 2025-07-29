USE soccer_bm;
CREATE TABLE IF NOT EXISTS dockers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    docker_name VARCHAR(100),
    docker_detail VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);