package com.alibaba.compileflow.engine.process.om.activity;

import com.alibaba.compileflow.engine.process.om.context.BusinessContext;
import org.springframework.stereotype.Service;

/**
 * @author yusu
 */
@Service
public abstract class BaseOmActivity {

    public void execute(BusinessContext businessContext) {
        System.out.println(this.getClass().getSimpleName() + " execute");
    }

}
