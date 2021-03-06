/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.common;

import org.drools.core.util.LinkedListNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.spi.Activation;
import org.drools.core.spi.PropagationContext;
import org.drools.core.time.JobHandle;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ScheduledAgendaItem extends AgendaItem
    implements
    Activation,
    Externalizable,
    LinkedListNode<ScheduledAgendaItem> {

    private static final long        serialVersionUID = 510l;

    private ScheduledAgendaItem      previous;

    private ScheduledAgendaItem      next;

//
    private InternalAgenda           agenda;

    private boolean                  enqueued;
    
    private JobHandle jobHandle;

    public ScheduledAgendaItem(final long activationNumber,
                               final LeftTuple tuple,
                               final InternalAgenda agenda,
                               final PropagationContext context,
                               final TerminalNode rtn) {
        super(activationNumber, tuple, 0, context, rtn );
        this.agenda = agenda;
        this.enqueued = false;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal( in );        
        agenda   = (InternalAgenda)in.readObject();
        enqueued = in.readBoolean();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal( out );
        out.writeObject( agenda );
        out.writeBoolean( enqueued );
    }
    public ScheduledAgendaItem getNext() {
        return this.next;
    }

    public void setNext(final ScheduledAgendaItem next) {
        this.next = next;
    }       

    public ScheduledAgendaItem getPrevious() {
        return this.previous;
    }

    public void setPrevious(final ScheduledAgendaItem previous) {
        this.previous = previous;
    }

    public void remove() {
        this.agenda.removeScheduleItem( this );
    }
    
    public JobHandle getJobHandle() {
        return this.jobHandle;
    }

    public void setJobHandle(JobHandle jobHandle) {
        this.jobHandle = jobHandle;
    }
    
    public String toString() {
        return "[ScheduledActivation rule=" + getRule().getName() + ", tuple=" + getTuple() + "]";
    }

    public boolean isEnqueued() {
        return enqueued;
    }

    public void setEnqueued( boolean enqueued ) {
        this.enqueued = enqueued;
    }
}
