package br.ufma.lsdi.digitalphenotyping.phenotypecomposer;

import static br.ufma.lsdi.digitalphenotyping.CompositionMode.FREQUENCY;
import static br.ufma.lsdi.digitalphenotyping.CompositionMode.GROUP_ALL;
import static br.ufma.lsdi.digitalphenotyping.CompositionMode.SEND_WHEN_IT_ARRIVES;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import androidx.room.Room;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.listeners.IConnectionListener;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;
import br.ufma.lsdi.digitalphenotyping.CompositionMode;
import br.ufma.lsdi.digitalphenotyping.Topics;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.DistributePhenotypeWork;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.base.PublishPhenotype;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.database.AppDatabase;
import br.ufma.lsdi.digitalphenotyping.phenotypecomposer.database.Phenotypes;

public class PhenotypeComposer extends Service {
    private static final String TAG = PhenotypeComposer.class.getName();
    Publisher publisher = PublisherFactory.createPublisher();
    Subscriber subRawDataInferenceResult;
    Subscriber subCompositionMode;
    Subscriber subActiveDataProcessor;
    Subscriber subDeactivateDataProcessor;
    private Context context;
    PublishPhenotype publishPhenotype;
    private TextView messageTextView;
    private ConnectionImpl connectionBroker;
    private String statusConnection = "";
    private CompositionMode lastCompositionMode = SEND_WHEN_IT_ARRIVES;
    private int lastFrequency = 6;
    private List<String> nameActiveDataProcessors = new ArrayList();
    private List<Boolean> activeDataProcessors = new ArrayList();
    AppDatabase db;
    WorkManager workManager;

    @Override
    public void onCreate() {
        try {
            Log.i(TAG, "#### Started PhenotypeComposer Service");
            context = this;

            //Receives data inferred by DataProcessors
            subRawDataInferenceResult = SubscriberFactory.createSubscriber();
            subRawDataInferenceResult.addConnection(CDDL.getInstance().getConnection());

            // Monitor the CompositionMode
            subCompositionMode = SubscriberFactory.createSubscriber();
            subCompositionMode.addConnection(CDDL.getInstance().getConnection());

            // Monitor the Active DataProcessor
            subActiveDataProcessor = SubscriberFactory.createSubscriber();
            subActiveDataProcessor.addConnection(CDDL.getInstance().getConnection());

            // Monitor the Deactivate Processors
            subDeactivateDataProcessor = SubscriberFactory.createSubscriber();
            subDeactivateDataProcessor.addConnection(CDDL.getInstance().getConnection());

            messageTextView = new TextView(context);

            db = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "database-phenotype").build();
        }catch (Exception e){
            Log.e(TAG,"Error: " + e.toString());
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "#### CONFIGURATION PHENOTYPECOMPOSER SERVICE");

        subscribeMessageRawDataInferenceResult(Topics.INFERENCE_TOPIC.toString());
        subscribeMessageCompositionMode(Topics.COMPOSITION_MODE_TOPIC.toString());

        subscribeMessageAtiveDataProcessor(Topics.ACTIVE_DATAPROCESSOR_TOPIC.toString());
        subscribeMessageDeactivateDataProcessor(Topics.DEACTIVATE_DATAPROCESSOR_TOPIC.toString());

        connectionBroker();

        //manager(lastCompositionMode, lastFrequency);

        publishMessage(Topics.MAINSERVICE_COMPOSITIONMODE_TOPIC.toString(), "alive");

        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public IBinder onBind(Intent intent) { return null; }


    public void connectionBroker(){
        try {
            //String host = "broker.hivemq.com";
            String host = "192.168.0.7";
            Log.i(TAG,"#### ENDEREÇO DO BROKER: " + host);
            connectionBroker = ConnectionFactory.createConnection();
            connectionBroker.setClientId("febfcfbccaeabda");
            Log.i(TAG,"#### clientID:  " + connectionBroker.getClientId());
            //Log.i(TAG,"#### clientID CDDL:  " + CDDL.getInstance().getConnection().getClientId());
            connectionBroker.setHost(host);
            connectionBroker.setPort("1883");
            connectionBroker.addConnectionListener(connectionListener);

            long automaticReconnectionTime = 1000L;
            int connectionTimeout = 30;
            int keepAliveInterval = 60;
            boolean automaticReconnection = true;
            boolean publishConnectionChangedStatus = false;
            int maxInflightMessages = 10;
            int mqttVersion =3;

            connectionBroker.connect("tcp",host,"1883", automaticReconnection,automaticReconnectionTime,false,connectionTimeout,
                    keepAliveInterval,publishConnectionChangedStatus,maxInflightMessages,"","",mqttVersion);

            Log.i(TAG,"#### CONECTADO: " + connectionBroker.isConnected());
            if(!connectionBroker.isConnected()){
                Log.i(TAG,"#### RECONNECT BROKER...");
                connectionBroker.reconnect();
            }

            publishPhenotype = new PublishPhenotype(connectionBroker, context);
        }catch (Exception e){
            Log.e(TAG,"#### Error: " + e.getMessage());
        }
    }


    public void manager(CompositionMode compositionMode, int frequency){
        Log.i(TAG,"#### Manager PhenotypeComposer");
        if(lastCompositionModeDifferent()){
            if(compositionMode == SEND_WHEN_IT_ARRIVES){

            }
            else if(compositionMode == GROUP_ALL){

            }
            else if(compositionMode == FREQUENCY){
                startWorkManager();
            }
        }
    }


    public boolean lastCompositionModeDifferent(){
        if(false){
            return false;
        }
        return true;
    }


    public void startWorkManager(){
        Log.i(TAG,"#### Started WorkManager");
        // Adicionamos restrições ao Work: 1 - esteja conectado a internet,
        //                                  2 - o nível de baterial não pode estar baixa.
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        // O Work executa periodicamente, caso uma das exigências não for atendida, tenta executar (exponencial ou linear) novamente o Work.
        PeriodicWorkRequest saveRequest =
                new PeriodicWorkRequest.Builder(DistributePhenotypeWork.class, this.lastFrequency, TimeUnit.MINUTES)
                        .addTag("distributephenotypework")
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                                TimeUnit.SECONDS)
                        .build();

        WorkManager.getInstance(this.context)
                .enqueue(saveRequest);

        workManager = WorkManager.getInstance(this.context);
    }


    public void stopWorkManager(){
        workManager.cancelAllWorkByTag("distributephenotypework");
    }


    private IConnectionListener connectionListener = new IConnectionListener() {
        @Override
        public void onConnectionEstablished() {
            statusConnection = "Established connection.";
            messageTextView.setText("Conexão estabelecida.");
            Log.i(TAG,"#### Status CDDL: " + statusConnection);
        }

        @Override
        public void onConnectionEstablishmentFailed() {
            statusConnection = "Failed connection.";
            messageTextView.setText("Falha na conexão.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }

        @Override
        public void onConnectionLost() {
            statusConnection = "Lost connection.";
            messageTextView.setText("Conexão perdida.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }

        @Override
        public void onDisconnectedNormally() {
            statusConnection = "A normal disconnect has occurred.";
            messageTextView.setText("Uma disconexão normal ocorreu.");
            Log.i(TAG,"#### Status MQTT: " + statusConnection);
        }
    };


    public void subscribeMessageRawDataInferenceResult(String serviceName) {
        subRawDataInferenceResult.subscribeServiceByName(serviceName);
        subRawDataInferenceResult.setSubscriberListener(subscriberRawDataInferenceResultListener);
        subRawDataInferenceResult.subscribeTopic(Topics.INFERENCE_TOPIC.toString());
    }


    public void subscribeMessageCompositionMode(String serviceName) {
        subCompositionMode.subscribeServiceByName(serviceName);
        subCompositionMode.setSubscriberListener(subscriberCompositionModeListener);
        subCompositionMode.subscribeTopic(Topics.COMPOSITION_MODE_TOPIC.toString());
    }


    public void subscribeMessageAtiveDataProcessor(String serviceName) {
        subActiveDataProcessor.subscribeServiceByName(serviceName);
        subActiveDataProcessor.setSubscriberListener(subscriberActiveDataProcessorsListener);
    }


    public void subscribeMessageDeactivateDataProcessor(String serviceName) {
        subDeactivateDataProcessor.subscribeServiceByName(serviceName);
        subDeactivateDataProcessor.setSubscriberListener(subscriberDeactivateDataProcessorsListener);
    }


    public ISubscriberListener subscriberRawDataInferenceResultListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Log.i(TAG, "#### Read messages (subscriber RawDataInferenceResult Listener):  " + message);
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade = String.valueOf(separated[0]);

            if(lastCompositionMode == SEND_WHEN_IT_ARRIVES){
                publishPhenotype.getInstance().publishPhenotypeComposer(message);
            }
            else if(lastCompositionMode == GROUP_ALL){
                int position = nameActiveDataProcessors.indexOf(atividade);
                activeDataProcessors.set(position,true);
                Phenotypes phenotype = new Phenotypes();

                if(activeDataProcessors.size() != 0) {
                    if (!activeDataProcessors.isEmpty()) {
                        boolean all = true;
                        for (int i = 0; i <= activeDataProcessors.size(); i++) {
                            if (!activeDataProcessors.get(i).booleanValue()) {
                                if (!activeDataProcessors.contains(false)) {
                                    all = true;
                                } else {
                                    all = false;
                                }
                                break;
                            }
                            break;
                        }
                        if (all) {
                            publishPhenotype.getInstance().publishPhenotypeComposer(message);

                            // Retrieve information
                            phenotype = db.phenotypeDAO().findByPhenotypeAll();
                            while (phenotype != null) {
                                String stringPhenotype = phenotype.getPhenotype();
                                int i = 0;
                                Message msg = phenotype.getObjectFromString(stringPhenotype);

                                // Publish the information
                                publishPhenotype.getInstance().publishPhenotypeComposer(msg);

                                // Remove from database
                                db.phenotypeDAO().delete(phenotype);

                                phenotype = db.phenotypeDAO().findByPhenotypeAll();
                            }

                            for (int j = 0; j < activeDataProcessors.size(); j++) {
                                activeDataProcessors.set(j, false);
                            }
                        } else {
                            //Save phenotype
                            Phenotypes phenotypes = new Phenotypes();
                            phenotypes.stringFromObject(message);
                            db.phenotypeDAO().insertAll(phenotypes);
                        }
                    }
                }
                else{
                    //Publish phenotype
                    publishPhenotype.getInstance().publishPhenotypeComposer(message);
                }
            }
            else if(lastCompositionMode == FREQUENCY){
                //Save phenotype
                Phenotypes phenotypes = new Phenotypes();
                phenotypes.stringFromObject(message);
                db.phenotypeDAO().insertAll(phenotypes);
            }
        }
    };


    public final ISubscriberListener subscriberCompositionModeListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            //Log.i(TAG, "#### Read messages (subscriber CompositionMode):  " + message);
            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade1 = String.valueOf(separated[0]);

            lastCompositionMode = CompositionMode.valueOf(atividade1);
            Double n = (Double) valor[1];
            lastFrequency = n.intValue();
            Log.i(TAG, "#### Value lastCompositionMode: " + lastCompositionMode.name().toString() + ", Value frequency: " + lastFrequency);

            manager(lastCompositionMode, lastFrequency);
        }
    };


    public ISubscriberListener subscriberActiveDataProcessorsListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Log.i(TAG, "#### Read messages (active Processor):  " + message);

            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            Log.i(TAG, "#### " + mensagemRecebida);
            String[] separated = mensagemRecebida.split(",");
            String atividade = String.valueOf(separated[0]);

            nameActiveDataProcessors.add(atividade);
            activeDataProcessors.add(false);
        }
    };


    public ISubscriberListener subscriberDeactivateDataProcessorsListener = new ISubscriberListener() {
        @Override
        public void onMessageArrived(Message message) {
            Log.i(TAG, "#### Read messages (deactivate processor):  " + message);

            Object[] valor = message.getServiceValue();
            String mensagemRecebida = StringUtils.join(valor, ", ");
            String[] separated = mensagemRecebida.split(",");
            String atividade = String.valueOf(separated[0]);

            nameActiveDataProcessors.remove(atividade);
            activeDataProcessors.remove(false);
        }
    };


    public void publishMessage(String service, String text) {
        publisher.addConnection(CDDL.getInstance().getConnection());

        Message message = new Message();
        message.setServiceName(service);
        message.setServiceValue(text);
        publisher.publish(message);
    }
}
