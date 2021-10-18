package br.ufma.lsdi.digitalphenotyping.dataprocessor.processors;

import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.digitalphenotyping.dataprocessor.base.DataProcessor;

public class Sleep extends DataProcessor {
    private static final String TAG = Sleep.class.getName();
    int i=0;

    @Override
    public void init(){
        try {
            Log.i(TAG, "#### Running processor Sleep");

            setDataProcessorName("Sleep");

            List<String> sensorList = new ArrayList();
            sensorList.add("TouchScreen");
            sensorList.add("SMS");
            startSensor(sensorList);
        }catch (Exception e){
            Log.e(TAG, "Error: " + e.toString());
        }
    }


    @Override
    public void onSensorDataArrived(Message message){
        processedDataMessage(message);
    }


    @Override
    public void processedDataMessage(Message message){
        Log.i(TAG,"#### MSG ORIGINAL SLEEP: " + message);
        DigitalPhenotypeEvent digitalPhenotypeEvent = new DigitalPhenotypeEvent();
        digitalPhenotypeEvent.setUid(CDDL.getInstance().getConnection().getClientId());

        Situation situation = new Situation();
        situation.setLabel("Touch");
        situation.setDescription("Screen touch detection");
        digitalPhenotypeEvent.setSituation(situation);

        Object[] valor1 = message.getServiceValue();
        String mensagemRecebida1 = StringUtils.join(valor1, ", ");
        String[] listValues = mensagemRecebida1.split(",");

        String[] valor2 = message.getAvailableAttributesList();
        String mensagemRecebida2 = StringUtils.join(valor2, ", ");
        String[] listAttributes = mensagemRecebida2.split(",");

        /*if(!listAttrutes[0].isEmpty() && !listValues[0].isEmpty()) {
            digitalPhenotypeEvent.setAttributes(listAttrutes[0], listValues[0], "String", false);
        }*/
        if(!listAttributes[2].isEmpty() && !listValues[2].isEmpty()) {
            Log.i(TAG,"#### listAttributes[1]: " + listAttributes[2]);
            Log.i(TAG,"#### listValues[1]: " + listValues[2]);
            digitalPhenotypeEvent.setAttributes(listAttributes[2], listValues[2], "Date", false);
        }

        String json = toJson(digitalPhenotypeEvent);
        Message msg = new Message();
        msg.setServiceValue(json);
        sendProcessedData(msg);
    }


    @Override
    public void end(){ }
}
