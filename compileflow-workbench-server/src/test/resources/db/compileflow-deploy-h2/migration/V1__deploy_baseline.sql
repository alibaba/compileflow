-- H2-only adapter for Workbench composition tests.
-- Production authority remains one explicitly selected PostgreSQL or MySQL Provider.
RUNSCRIPT FROM 'classpath:db/compileflow-deploy-h2/schema.sql';
