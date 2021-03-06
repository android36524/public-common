package com.sgck.notification.consumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerOrderly;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.consumer.ConsumeFromWhere;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.protocol.ResponseCode;
import com.alibaba.rocketmq.common.protocol.heartbeat.MessageModel;
import com.sgck.notification.NotificationHandler;
import com.sgck.notification.annotation.NotificationCallback;

public class SGConsumer implements ApplicationContextAware{

    private final Logger logger = LoggerFactory.getLogger(SGConsumer.class);

    private DefaultMQPushConsumer defaultMQPushConsumer;
    private String namesrvAddr = "127.0.0.1:9876";
    private String consumerGroup = "sgck-notify-service";

    private String topic = "DefaultTopic";
    private List<String> topicList = null;
	private String tag = null;
    private String msgMode = "cluster"; // cluster
    
    private ApplicationContext applicationContext;
    private Map<String,List<Object>> handlerObjMap = null;
    
    /**
     * Spring bean init-method
     */
    public void init() throws InterruptedException, MQClientException {

        defaultMQPushConsumer = new DefaultMQPushConsumer(consumerGroup);
        defaultMQPushConsumer.setNamesrvAddr(namesrvAddr);
        defaultMQPushConsumer.setInstanceName(String.valueOf(System.currentTimeMillis()));

        if(topicList != null){
        	for(String topic : topicList){
        		defaultMQPushConsumer.subscribe(topic, null);
        	}
        }else{
        	defaultMQPushConsumer.subscribe(topic, tag);
        }

        defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET); // CONSUME_FROM_FIRST_OFFSET 

        if(msgMode.equalsIgnoreCase("broadcast")){
        	defaultMQPushConsumer.setMessageModel(MessageModel.BROADCASTING);
        }else{
        	defaultMQPushConsumer.setMessageModel(MessageModel.CLUSTERING);
        }
        
        
//        defaultMQPushConsumer.registerMessageListener(new MessageListenerConcurrently() {
//            @Override
//            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
//
//                MessageExt msg = msgs.get(0);
////                if (msg.getTopic().equals(topic)) {
////                    if (tag == null || (msg.getTags() != null && msg.getTags().equals(tag))) {
////                    	SGConsumer.this.handleNotification(msg.getTags(),msg.getBody());
////                    }
////                }
//                SGConsumer.this.handleNotification(msg.getTopic(),msg.getTags(),msg.getBody());
//                // 锟斤拷锟矫伙拷锟絩eturn success 锟斤拷consumer锟斤拷锟斤拷锟斤拷锟斤拷迅锟斤拷锟较拷锟街憋拷锟絩eturn success
//                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//        });
        
        defaultMQPushConsumer.registerMessageListener(new MessageListenerOrderly() {
            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            	context.setAutoCommit(true);
                MessageExt msg = msgs.get(0);
                SGConsumer.this.handleNotification(msg.getTopic(),msg.getTags(),msg.getBody());
                return ConsumeOrderlyStatus.SUCCESS;
            }
        });

        defaultMQPushConsumer.start();

        new Thread(new Runnable() {
			@Override
			public void run() {
				if(topicList != null){
		        	for(String topic : topicList){
		        		createTopicIfAbsent(topic);
		        	}
		        }else{
		        	createTopicIfAbsent(topic);
		        }
			}
		}).start();
        
        logger.info("SGConsumer OK to start with topic[" + topic  + "]");
    }

    /**
     * Spring bean destroy-method
     */
    public void destroy() {
        defaultMQPushConsumer.shutdown();
    }

    public void subscribe(String topic,String tag){
    	if(defaultMQPushConsumer != null){
    		try {
				defaultMQPushConsumer.subscribe(topic, tag);
			} catch (MQClientException e) {
				logger.error("SGConsumer::subscribe",e);
			}
    	}
    }
    
    private void handleNotification(String topic,String tag,byte[] content){
    	List<Object> handlerObjList = null;
    	if(handlerObjMap == null){
			try {
				Map<String,Object> beans = applicationContext.getBeansWithAnnotation(NotificationCallback.class);
	    		
	    		Map<String,List<Object>> candidate = new HashMap<String,List<Object>>();
	    		String annotationTopic = null;
//	    		String annotationTag = null;
	    		for(Object bean : beans.values()){
	    			if(!(bean instanceof NotificationHandler)){
	    				continue;
	    			}
	    			
	    			NotificationCallback consumerAnnotation = null;
	    			try{
	    				consumerAnnotation = bean.getClass().getAnnotation(NotificationCallback.class);
	    			} catch (Exception e) {
	    				continue;
	    			}
	    			
	    			if(consumerAnnotation == null){
	    				continue;
	    			}
	    			
	    			if((annotationTopic = consumerAnnotation.topic()).isEmpty()){
	    				annotationTopic = consumerAnnotation.value();
	    			}
	    			
	    			if(annotationTopic.isEmpty()){
	    				continue;
	    			}
	    			
	    			if((handlerObjList = (List<Object>)candidate.get(annotationTopic)) == null){
	    				handlerObjList = new ArrayList<Object>();
	    				candidate.put(annotationTopic, handlerObjList);
	    			}
	    			
	    			handlerObjList.add(bean);
	    			
//	    			if(!topic.equals(annotationTopic)){
//	    				continue;
//	    			}
//	    			
//	    			annotationTag = consumerAnnotation.tag();
//	    			if(tag != null && !annotationTag.isEmpty() && !tag.equals(annotationTag)){
//	    				continue;
//	    			}
//	    			if(tag == null && !annotationTag.isEmpty()){
//	    				continue;
//	    			}
	    			
	    		}
	    		
	    		if(candidate.isEmpty()){
	    			logger.error("handleNotification: no handler for topic[" + topic + "]");
	    			return;
	    		}
	    		
	    		handlerObjMap = candidate;
	    		
			} catch (Exception e) {
				logger.error("handleNotification: " ,e);
				return;
			} 
    	}
    	
    	if(handlerObjMap != null && (handlerObjList = (List<Object>)handlerObjMap.get(topic)) != null){
    		for(Object handler : handlerObjList){
    			NotificationCallback consumerAnnotation = handler.getClass().getAnnotation(NotificationCallback.class);
    			String annotationTag = consumerAnnotation.tag();
    			if(tag != null && !annotationTag.isEmpty() && !tag.equals(annotationTag)){
					continue;
				}
				if(tag == null && !annotationTag.isEmpty()){
					continue;
				}
    			
    			((NotificationHandler)handler).onNotification(topic, tag, content);
    		}
    	}
    }
    
    // ----------------- setter --------------------

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public void setTopicList(List<String> topicList) {
		this.topicList = topicList;
	}

	public String getMsgMode() {
		return msgMode;
	}

	public void setMsgMode(String msgMode) {
		this.msgMode = msgMode;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	private void createTopicIfAbsent(String topic){
		try {
			defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getmQClientFactory().getMQClientAPIImpl().getTopicRouteInfoFromNameServer(topic, 3000);
		} catch (MQClientException e1) {
			if(ResponseCode.TOPIC_NOT_EXIST == e1.getResponseCode()){
				try {
        			defaultMQPushConsumer.createTopic(MixAll.DEFAULT_TOPIC,topic,4);
				} catch (Exception e) {
					logger.error("create topic in advance: ",e);
				} 
			}else{
				logger.error("getTopicRouteInfoFromNameServer MQClientException: ",e1);
			}
		} catch (Exception e) {
			logger.error("getTopicRouteInfoFromNameServer: ",e);
		}
	}
}