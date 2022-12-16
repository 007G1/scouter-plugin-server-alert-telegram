// 
// Decompiled by Procyon v0.5.36
// 

package scouter.plugin.server.alert.telegram;

import scouter.server.CounterManager;
import scouter.util.HashUtil;
import scouter.lang.pack.PerfCounterPack;
import scouter.server.Logger;
import scouter.server.db.TextRD;
import scouter.util.DateUtil;
import scouter.lang.pack.XLogPack;
import scouter.server.core.AgentManager;
import scouter.lang.pack.ObjectPack;
import scouter.lang.plugin.annotation.ServerPlugin;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import scouter.lang.pack.AlertPack;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import scouter.server.Configure;

public class TelegramPlugin
{
    final Configure conf;
    private static AtomicInteger ai;
    private static List<Integer> javaeeObjHashList;
    private static AlertPack lastPack;
    private static long lastSentTimestamp;
    
    static {
        TelegramPlugin.ai = new AtomicInteger(0);
        TelegramPlugin.javaeeObjHashList = new ArrayList<Integer>();
    }
    
    public TelegramPlugin() {
        this.conf = Configure.getInstance();
        if (TelegramPlugin.ai.incrementAndGet() == 1) {
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate((Runnable)new TelegramPlugin.TelegramPlugin$1(this), 0L, 5L, TimeUnit.SECONDS);
        }
    }
    
    @ServerPlugin("pluginServerAlert")
    public void alert(final AlertPack pack) {
        if (this.conf.getBoolean("ext_plugin_telegram_send_alert", false)) {
            final int level = this.conf.getInt("ext_plugin_telegram_level", 0);
            if (level <= pack.level) {
                new TelegramPlugin.TelegramPlugin$2(this, pack).start();
            }
        }
    }
    
    @ServerPlugin("pluginServerObject")
    public void object(final ObjectPack pack) {
        if (pack.version != null && pack.version.length() > 0) {
            AlertPack ap = null;
            final ObjectPack op = AgentManager.getAgent(pack.objHash);
            if (op == null && pack.wakeup == 0L) {
                ap = new AlertPack();
                ap.level = 0;
                ap.objHash = pack.objHash;
                ap.title = "An object has been activated.";
                ap.message = String.valueOf(pack.objName) + " is connected.";
                ap.time = System.currentTimeMillis();
                if (AgentManager.getAgent(pack.objHash) != null) {
                    ap.objType = AgentManager.getAgent(pack.objHash).objType;
                }
                else {
                    ap.objType = "scouter";
                }
                this.alert(ap);
            }
            else if (!op.alive) {
                ap = new AlertPack();
                ap.level = 0;
                ap.objHash = pack.objHash;
                ap.title = "An object has been activated.";
                ap.message = String.valueOf(pack.objName) + " is reconnected.";
                ap.time = System.currentTimeMillis();
                ap.objType = AgentManager.getAgent(pack.objHash).objType;
                this.alert(ap);
            }
        }
    }
    
    @ServerPlugin("pluginServerXLog")
    public void xlog(final XLogPack pack) {
        try {
            final int elapsedThreshold = this.conf.getInt("ext_plugin_elapsed_time_threshold", 0);
            if (elapsedThreshold != 0 && pack.elapsed > elapsedThreshold) {
                final String serviceName = TextRD.getString(DateUtil.yyyymmdd(pack.endTime), "service", pack.service);
                final AlertPack ap = new AlertPack();
                ap.level = 1;
                ap.objHash = pack.objHash;
                ap.title = "Elapsed time exceed a threshold.";
                ap.message = "[" + AgentManager.getAgentName(pack.objHash) + "] " + pack.service + "(" + serviceName + ") " + "elapsed time(" + pack.elapsed + " ms) exceed a threshold.";
                ap.time = System.currentTimeMillis();
                ap.objType = AgentManager.getAgent(pack.objHash).objType;
                this.alert(ap);
            }
        }
        catch (Exception e) {
            Logger.printStackTrace((Throwable)e);
        }
    }
    
    @ServerPlugin("pluginServerCounter")
    public void counter(final PerfCounterPack pack) {
        final String objName = pack.objName;
        final int objHash = HashUtil.hash(objName);
        String objType = null;
        String objFamily = null;
        if (AgentManager.getAgent(objHash) != null) {
            objType = AgentManager.getAgent(objHash).objType;
        }
        if (objType != null) {
            objFamily = CounterManager.getInstance().getCounterEngine().getObjectType(objType).getFamily().getName();
        }
        try {
            if ("javaee".equals(objFamily)) {
                if (!TelegramPlugin.javaeeObjHashList.contains(objHash)) {
                    TelegramPlugin.javaeeObjHashList.add(objHash);
                }
                if (pack.timetype == 1) {
                    final long gcTimeThreshold = this.conf.getLong("ext_plugin_gc_time_threshold", 0L);
                    final long gcTime = pack.data.getLong("GcTime");
                    if (gcTimeThreshold != 0L && gcTime > gcTimeThreshold) {
                        final AlertPack ap = new AlertPack();
                        ap.level = 1;
                        ap.objHash = objHash;
                        ap.title = "GC time exceed a threshold.";
                        ap.message = String.valueOf(objName) + "'s GC time(" + gcTime + " ms) exceed a threshold.";
                        ap.time = System.currentTimeMillis();
                        ap.objType = objType;
                        this.alert(ap);
                    }
                }
            }
        }
        catch (Exception e) {
            Logger.printStackTrace((Throwable)e);
        }
    }
    
    private void println(final Object o) {
        if (this.conf.getBoolean("ext_plugin_telegram_debug", false)) {
            Logger.println(o);
        }
    }
}