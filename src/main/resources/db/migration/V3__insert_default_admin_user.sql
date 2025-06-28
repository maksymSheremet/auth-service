-- Insert default admin user
-- Insert default admin user
INSERT INTO users (firstname, lastname, email, password, provider, role)
VALUES ('Admin',
        'User',
        'admin@example.com',
        '$2a$10$kbXyEzp23vqSP4HaHJlJq.JsEmy6gRdT28HM0RrdV2Z1DBCClCT/G', -- Password: Admin123!
        'LOCAL',
        'ADMIN');