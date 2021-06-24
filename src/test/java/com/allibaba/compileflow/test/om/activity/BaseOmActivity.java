package com.allibaba.compileflow.test.om.activity;

import com.allibaba.compileflow.test.om.context.BusinessContext;
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
