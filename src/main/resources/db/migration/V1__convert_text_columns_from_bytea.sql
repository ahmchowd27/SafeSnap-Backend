-- Migration: Convert any mistakenly created BYTEA text columns to proper TEXT
-- Safe conditional conversion; only alters if column is currently bytea.
-- Assumes stored data was UTF-8 encoded originally.

DO $$
BEGIN
    -- incidents.title
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='incidents' AND column_name='title' AND data_type='bytea'
    ) THEN
        ALTER TABLE incidents
            ALTER COLUMN title TYPE text USING convert_from(title, 'UTF8');
    END IF;

    -- incidents.description
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='incidents' AND column_name='description' AND data_type='bytea'
    ) THEN
        ALTER TABLE incidents
            ALTER COLUMN description TYPE text USING convert_from(description, 'UTF8');
    END IF;

    -- incidents.location_description
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='incidents' AND column_name='location_description' AND data_type='bytea'
    ) THEN
        ALTER TABLE incidents
            ALTER COLUMN location_description TYPE text USING convert_from(location_description, 'UTF8');
    END IF;

    -- ai_suggestions.summary
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='ai_suggestions' AND column_name='summary' AND data_type='bytea'
    ) THEN
        ALTER TABLE ai_suggestions
            ALTER COLUMN summary TYPE text USING convert_from(summary, 'UTF8');
    END IF;

    -- ai_suggestions.keywords
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='ai_suggestions' AND column_name='keywords' AND data_type='bytea'
    ) THEN
        ALTER TABLE ai_suggestions
            ALTER COLUMN keywords TYPE text USING convert_from(keywords, 'UTF8');
    END IF;

    -- voice_transcriptions.transcription_text (if present in schema)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='voice_transcriptions' AND column_name='transcription_text' AND data_type='bytea'
    ) THEN
        ALTER TABLE voice_transcriptions
            ALTER COLUMN transcription_text TYPE text USING convert_from(transcription_text, 'UTF8');
    END IF;
END $$;

