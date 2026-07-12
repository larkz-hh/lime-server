package com.lzz.lime_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务类
 * 供验证码邮件的发送功能
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;// 邮件发送器

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 发送验证码邮件
     * 构建纯文本验证码邮件并发送至指定收件人邮箱
     * 邮件内容包含验证码及有效期提示
     * @param to   收件人邮箱地址
     * @param code 验证码字符串
     */
    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("【Lime】验证码");
        message.setText("您的验证码为：" + code + "，5 分钟内有效，请勿泄露给他人。");
        mailSender.send(message);// 调用邮件发送器发送邮件
    }
}
