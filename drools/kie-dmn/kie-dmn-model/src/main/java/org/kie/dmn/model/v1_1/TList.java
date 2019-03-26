/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.dmn.model.v1_1;

import java.util.ArrayList;

import org.kie.dmn.model.api.Expression;
import org.kie.dmn.model.api.List;

/**
 * Representation for DMN XSD tList type.
 */
public class TList extends TExpression implements List {

    private java.util.List<Expression> expression;

    @Override
    public java.util.List<Expression> getExpression() {
        if ( expression == null ) {
            expression = new ArrayList<>();
        }
        return this.expression;
    }

}
