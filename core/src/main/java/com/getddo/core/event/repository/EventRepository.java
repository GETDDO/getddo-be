package com.getddo.core.event.repository;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;

public interface EventRepository {
	RegisteredEvent create(EventRegistration registration, EventStatus initialStatus);
}
