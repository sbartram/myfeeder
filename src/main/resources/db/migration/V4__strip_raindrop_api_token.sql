UPDATE integration_config
SET config = jsonb_build_object(
    'collectionId', (config::jsonb->>'collectionId')::bigint
)::text
WHERE type = 'RAINDROP'
  AND config IS NOT NULL
  AND config::jsonb ? 'apiToken';
