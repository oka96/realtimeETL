package com.example.realtimeetl.etl;

public interface EtlFailureEventPublisher {

	String BINDING_NAME = "etlFailures-out-0";

	boolean publish(EtlFailureEvent event);
}
