package org.nobilis.nobichat.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

@Service
@Slf4j
public class WebPageContentExtractorService {

    private static final int TIMEOUT_MS = 60000; // 15 секунд
    private static final int MAX_BODY_SIZE_BYTES = 30 * 1024 * 1024; // 2 MB
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * Скачивает HTML-страницу по URL, извлекает из нее текст и возвращает его.
     * Включает меры безопасности для предотвращения SSRF.
     *
     * @param urlString URL страницы для парсинга.
     * @return Извлеченный текст.
     * @throws IOException              если происходит сетевая ошибка или ошибка парсинга.
     * @throws IllegalArgumentException если URL невалиден или ведет на приватный IP-адрес.
     */
    public String extractTextFromUrl(String urlString) throws IOException {
        log.info("Попытка извлечь текст со страницы по URL: {}", urlString);

        URL url = validateAndSecureUrl(urlString);

        try {
            Connection connection = Jsoup.connect(url.toString())
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE_BYTES);

            Document doc = connection.get();
            String text = doc.text();

            log.info("Успешно извлечено {} символов текста со страницы {}", text.length(), urlString);
            return text;

        } catch (SocketTimeoutException e) {
            log.error("Таймаут при подключении к URL: {}", urlString, e);
            throw new IOException("Не удалось подключиться к указанному URL за " + TIMEOUT_MS / 1000 + " секунд.", e);
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода при работе с URL: {}", urlString, e);
            throw new IOException("Не удалось загрузить данные по указанной ссылке. Проверьте ее доступность.", e);
        }
    }

    /**
     * Проверяет URL на валидность и безопасность (защита от SSRF).
     *
     * @param urlString URL в виде строки.
     * @return Объект URL, если все проверки пройдены.
     * @throws MalformedURLException    если URL имеет неверный формат.
     * @throws UnknownHostException     если не удалось разрешить хост.
     * @throws IllegalArgumentException если хост указывает на приватный или локальный IP-адрес.
     */
    private URL validateAndSecureUrl(String urlString) throws MalformedURLException, UnknownHostException {
        URL url = new URL(urlString);

        if (!url.getProtocol().toLowerCase().startsWith("http")) {
            throw new MalformedURLException("Поддерживаются только протоколы http и https.");
        }

        String host = url.getHost();
        InetAddress address = InetAddress.getByName(host);

        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            log.warn("Попытка доступа к приватному IP-адресу {} ({}) через URL {}. Запрос заблокирован.", address.getHostAddress(), host, urlString);
            throw new IllegalArgumentException("Доступ к локальным и приватным сетевым адресам запрещен.");
        }

        log.debug("URL {} разрешен в безопасный IP-адрес: {}", urlString, address.getHostAddress());
        return url;
    }
}
