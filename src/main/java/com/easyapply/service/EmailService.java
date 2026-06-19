package com.easyapply.service;

import java.io.IOException;

public interface EmailService {

	String sendBulkEmails() throws IOException, InterruptedException;
}
