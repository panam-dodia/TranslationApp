package com.panam.translationapp.utils

import android.content.Context
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.panam.translationapp.data.Session
import java.io.File
import java.time.format.DateTimeFormatter

class PDFExporter {

    companion object {
        fun exportSessionToPDF(context: Context, session: Session): File? {
            return try {
                // Create a file in the cache directory
                val fileName = "conversation_${session.id}_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)

                // Create PDF writer and document
                val writer = PdfWriter(file)
                val pdfDoc = PdfDocument(writer)
                val document = Document(pdfDoc)

                // Set margins
                document.setMargins(40f, 40f, 40f, 40f)

                // Add title
                val title = Paragraph("Conversation Export")
                    .setFontSize(24f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10f)
                document.add(title)

                // Add subtitle with session name
                val subtitle = Paragraph(session.getDisplayName())
                    .setFontSize(16f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
                document.add(subtitle)

                // Add session info table
                val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 2f)))
                    .setWidth(UnitValue.createPercentValue(100f))
                    .setMarginBottom(20f)

                addInfoRow(infoTable, "Languages:",
                    "${session.person1Language.displayName} ↔ ${session.person2Language.displayName}")
                addInfoRow(infoTable, "Date:",
                    session.createdAt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' h:mm a")))
                addInfoRow(infoTable, "Translations:",
                    session.translations.size.toString())
                addInfoRow(infoTable, "AI Questions:",
                    session.chatMessages.filter { it.isFromUser }.size.toString())

                document.add(infoTable)

                // Add separator
                document.add(Paragraph(" ").setMarginBottom(10f))

                // Add translations section
                if (session.translations.isNotEmpty()) {
                    val translationsHeader = Paragraph("Translations")
                        .setFontSize(18f)
                        .setBold()
                        .setMarginBottom(10f)
                        .setMarginTop(10f)
                    document.add(translationsHeader)

                    session.translations.forEachIndexed { index, translation ->
                        // Translation card
                        val translationTable = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                            .setWidth(UnitValue.createPercentValue(100f))
                            .setMarginBottom(15f)

                        // Time header
                        val timeCell = Cell()
                            .add(Paragraph("Translation #${index + 1} - ${translation.timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))}")
                                .setFontSize(10f)
                                .setFontColor(ColorConstants.GRAY))
                            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                            .setBackgroundColor(DeviceRgb(245, 245, 245))
                            .setPadding(8f)
                        translationTable.addCell(timeCell)

                        // Language 1 content
                        val lang1Cell = Cell()
                            .add(Paragraph(session.person1Language.displayName)
                                .setFontSize(10f)
                                .setBold()
                                .setFontColor(DeviceRgb(33, 150, 243)))
                            .add(Paragraph(translation.person1Text)
                                .setFontSize(12f)
                                .setMarginTop(4f))
                            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                            .setPadding(10f)
                        translationTable.addCell(lang1Cell)

                        // Language 2 content
                        val lang2Cell = Cell()
                            .add(Paragraph(session.person2Language.displayName)
                                .setFontSize(10f)
                                .setBold()
                                .setFontColor(DeviceRgb(76, 175, 80)))
                            .add(Paragraph(translation.person2Text)
                                .setFontSize(12f)
                                .setMarginTop(4f))
                            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                            .setPadding(10f)
                        translationTable.addCell(lang2Cell)

                        document.add(translationTable)
                    }
                }

                // Add AI conversations section
                if (session.chatMessages.isNotEmpty()) {
                    // Add page break if there are translations
                    if (session.translations.isNotEmpty()) {
                        document.add(AreaBreak())
                    }

                    val chatHeader = Paragraph("AI Conversations")
                        .setFontSize(18f)
                        .setBold()
                        .setMarginBottom(10f)
                        .setMarginTop(10f)
                    document.add(chatHeader)

                    session.chatMessages.forEach { message ->
                        val isUser = message.isFromUser
                        val sender = if (isUser) "You" else "AI Assistant"
                        val time = message.timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))

                        val messageTable = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                            .setWidth(UnitValue.createPercentValue(if (isUser) 80f else 100f))
                            .setMarginBottom(10f)

                        if (!isUser) {
                            messageTable.setMarginRight(0f)
                        } else {
                            messageTable.setMarginLeft(60f)
                        }

                        val messageCell = Cell()
                            .add(Paragraph("$sender - $time")
                                .setFontSize(9f)
                                .setBold()
                                .setFontColor(if (isUser) DeviceRgb(33, 150, 243) else DeviceRgb(102, 102, 102)))
                            .add(Paragraph(message.text)
                                .setFontSize(11f)
                                .setMarginTop(4f))
                            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                            .setBackgroundColor(if (isUser)
                                DeviceRgb(227, 242, 253) else
                                DeviceRgb(245, 245, 245))
                            .setPadding(10f)

                        messageTable.addCell(messageCell)
                        document.add(messageTable)
                    }
                }

                // Add footer
                document.add(Paragraph(" ").setMarginTop(20f))
                val footer = Paragraph("Generated by Translation App")
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                document.add(footer)

                // Close document
                document.close()

                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun addInfoRow(table: Table, label: String, value: String) {
            val labelCell = Cell()
                .add(Paragraph(label).setFontSize(11f).setBold())
                .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setBackgroundColor(DeviceRgb(245, 245, 245))
                .setPadding(8f)

            val valueCell = Cell()
                .add(Paragraph(value).setFontSize(11f))
                .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(8f)

            table.addCell(labelCell)
            table.addCell(valueCell)
        }
    }
}
