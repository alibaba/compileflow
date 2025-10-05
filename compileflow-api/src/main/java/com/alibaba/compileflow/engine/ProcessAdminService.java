package com.alibaba.compileflow.engine;

/**
 * Provides administrative services for managing the lifecycle of process flows.
 * <p>
 * This interface separates administrative concerns, such as pre-compilation (cache warming)
 * and hot-reloading (re-compilation), from the core execution tasks handled by the
 * {@link ProcessEngine}. It is typically used during application startup or for
 * operational management tasks.
 *
 * @author yusu
 * @see ProcessEngine
 */
public interface ProcessAdminService {

    /**
     * Deploys one or more processes from {@link ProcessSource} objects.
     * <p>
     * This allows for deploying a mix of code-based and content-based
     * flows in a single operation, using the engine's default ClassLoader.
     *
     * @param sources A variable number of {@link ProcessSource} objects to deploy.
     */
    void deploy(ProcessSource... sources);

    /**
     * Deploys one or more processes from {@link ProcessSource} objects
     * using a specific ClassLoader.
     *
     * @param classLoader The ClassLoader to use for this deployment.
     * @param sources     A variable number of {@link ProcessSource} objects to deploy.
     */
    void deploy(ClassLoader classLoader, ProcessSource... sources);

}
