-- Reader view: cache of readable content extracted from the article's original page.
ALTER TABLE article ADD COLUMN extracted_content TEXT;
