package com.easyapply.reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.easyapply.config.UserProperties;
import com.easyapply.exception.ResumeReadException;
import com.easyapply.util.ResourcePathResolver;

@Component
public class ResumeReader {

	private final UserProperties userProperties;
	private final ResourcePathResolver resourcePathResolver;

	public ResumeReader(UserProperties userProperties, ResourcePathResolver resourcePathResolver) {
		this.userProperties = userProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	public String readResumeText() {
		Path resumePath = resourcePathResolver.resolveExternalPath(userProperties.getResumePath());
		if (!Files.exists(resumePath)) {
			throw new ResumeReadException("Resume not found at: " + resumePath);
		}

		String fileName = resumePath.getFileName().toString().toLowerCase();
		try {
			if (fileName.endsWith(".pdf")) {
				return readPdf(resumePath);
			}
			if (fileName.endsWith(".txt")) {
				return Files.readString(resumePath, StandardCharsets.UTF_8);
			}
			throw new ResumeReadException("Unsupported resume format: " + fileName + ". Use .pdf or .txt");
		} catch (ResumeReadException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ResumeReadException("Failed to read resume from: " + resumePath, ex);
		}
	}

	private String readPdf(Path resumePath) throws IOException {
		try (PDDocument document = Loader.loadPDF(resumePath.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);
			if (text == null || text.isBlank()) {
				throw new ResumeReadException("Resume PDF contains no readable text: " + resumePath);
			}
			return text.trim();
		}
	}
}
