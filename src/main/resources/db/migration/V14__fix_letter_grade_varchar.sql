-- V14: Fix letter_grade column type to VARCHAR to align with Hibernate mapping
ALTER TABLE quality_scores ALTER COLUMN letter_grade TYPE VARCHAR(1);
