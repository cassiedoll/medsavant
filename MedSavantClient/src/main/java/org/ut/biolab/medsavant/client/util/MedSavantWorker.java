/**
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.ut.biolab.medsavant.client.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.Timer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ut.biolab.medsavant.shared.model.ProgressStatus;

/**
 * SwingWorker wrapper which provides hooks do the right thing in response to errors, cancellation, etc.
 * 
 * @author tarkvara
 */
public abstract class MedSavantWorker<T> { //extends SwingWorker<T, Object> {
    private static final Log LOG = LogFactory.getLog(MedSavantWorker.class);
    private static final int MAX_MEDSAVANT_WORKER_THREADS = 20;
    
    private String pageName;
    private SwingWorker<T, Object> swingWorker;
    
    private static ExecutorService threadPool = Executors.newFixedThreadPool(MAX_MEDSAVANT_WORKER_THREADS);
    protected Timer progressTimer;

    /**
     * @param pageName which view created this worker
     */
    public MedSavantWorker(String pageName) {        
        this.pageName = pageName;
        ThreadController.getInstance().addWorker(pageName, this);
        
        final MedSavantWorker instance = this;
        swingWorker = new SwingWorker<T, Object>(){
            @Override
            public void done(){
                instance.done();
            }
            @Override
            protected T doInBackground() throws Exception {
                return (T)instance.doInBackground();                
            }            
        };                         
 
    }
    
    public void execute(){
        threadPool.submit(swingWorker);        
    }

    protected abstract T doInBackground() throws Exception;
    
    public boolean isDone(){
        return swingWorker.isDone();
    }
    
    public boolean cancel(boolean cancel){
        return swingWorker.cancel(cancel);
    }
    
    public StateValue getState(){
        return swingWorker.getState();
    }
    
    public boolean isCancelled(){        
        return swingWorker.isCancelled();
    }
   
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue){
        swingWorker.firePropertyChange(propertyName, oldValue, newValue);
    }
    
    public void addPropertyChangeListener(PropertyChangeListener pcl){
        swingWorker.addPropertyChangeListener(pcl);        
    }
    
    public final int getProgress(){
        return swingWorker.getProgress();
    }
    
    
    //@Override
    public void done() {
        if (this.progressTimer != null) {
            this.progressTimer.stop();
        }
        showProgress(1.0);
        try {
            if (!swingWorker.isCancelled()) {
                showSuccess(swingWorker.get());
            } else {
                // Send the server one last checkProgress call so that server knows that we've cancelled.
                try {
                    checkProgress();
                } catch (Exception ex) {
                    LOG.info("Ignoring exception thrown while cancelling.", ex);
                }
                throw new InterruptedException();
            }
        } catch (InterruptedException x) {
            showFailure(x);
        } catch (ExecutionException x) {
            showFailure(x.getCause());
        } finally {
            // Succeed or fail, we want to remove the worker from our page.
            ThreadController.getInstance().removeWorker(this.pageName, this);
        }
    }

    /**
     * Show progress during a lengthy operation. As a special case, pass 1.0 to remove the progress display.  
     * 
     * @param fract the fraction completed (1.0 to indicate full completion; -1.0 as special flag to indicate
     *        indeterminate progress-bar).
     */
    protected void showProgress(double fract){
        
    }

    /**
     * Called when the worker has successfully completed its task.
     * 
     * @param result the value returned by <code>doInBackground()</code>.
     */
    protected abstract void showSuccess(T result);

    /**
     * Called when the task has thrown an exception. Default behaviour is to log the exception and put up a dialog box.
     */
    protected void showFailure(Throwable t) {
        if (!(t instanceof InterruptedException)) {
            ClientMiscUtils.reportError("Exception thrown by background task: %s", t);
        }
    }

    /**
     * Base-class does no progress checking.
     */
    protected ProgressStatus checkProgress() throws Exception {
        return null;
    }

    /**
     * Workers which want intermittent progress checks should start a timer which will call checkProgress and
     * showProgress intermittently.
     */
    protected void startProgressTimer() {
        this.progressTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    ProgressStatus status = checkProgress();
                    if (status != null) {
                        showProgress(status.fractionCompleted);
                    }
                } catch (Exception ex) {
                    LOG.info("Ignoring exception thrown while checking for progress.", ex);
                }
            }
        });
        this.progressTimer.start();
    }
}
