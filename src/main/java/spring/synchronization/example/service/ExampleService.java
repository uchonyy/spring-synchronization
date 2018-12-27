package spring.synchronization.example.service;


import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spring.synchronization.example.repository.Client;
import spring.synchronization.example.repository.ClientRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * Сервис, демострирующий различные варианты синхронизаций запросов
 *
 * @author uchonyy@gmail.com
 *
 */
@Service
@Slf4j
public class ExampleService {
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    public RedissonClient redissonClient;
    private static ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     *
     * Пример 1. Без синхронизации
     *
     * в данном примере демоствриуется ситуация, когда несколько запросов (потоков)
     * одного клиента начнут создавать сущность Client и получат sql ошибку дубликации
     * т.к. поле clientId является unique
     *
     */
    public void example1(String clientId, String requestId){
        log.info("Начинаем обработку запроса requestId={} clientId={}", requestId, clientId);
        Client client = clientRepository.findByClientId(clientId);
        if(client == null){
            log.info("Создаем клиента: requestId={} clientId={}", requestId, requestId);
            client = clientRepository.save(new Client(clientId));
            log.info("Клиент успешно создан: requestId={} client={}", requestId, client);
        }
        log.info("Закончена обработка запроса requestId={} client={}", requestId, client);
    }

    /**
     *
     * Пример 2. Полная синхронизации
     *
     * в данном примере создание сущности Client происходит после синхронизации;
     * но блокируются все запросы (потоки), которым нужно выполнить создание,
     * даже если они будут создавать Client с разными clientId и никак друг с другом бы не конкурировали
     *
     */
    public void example2(String clientId, String requestId){
        log.info("Начинаем обработку запроса requestId={} clientId={}", requestId, clientId);
        Client client = clientRepository.findByClientId(clientId);
        if(client == null){
            synchronized (this){
                client = clientRepository.findByClientId(clientId);
                if(client == null){
                    log.info("Создаем клиента: requestId={} clientId={}", requestId, requestId);
                    client = clientRepository.save(new Client(clientId));
                    log.info("Клиент успешно создан: requestId={} client={}", requestId, client);
                }
            }
        }
        log.info("Закончена обработка запроса requestId={} client={}", requestId, client);
    }

    /**
     *
     * Пример 3. Синхронизации по clientId. Вариант 1.
     *
     * в данном примере синхронизация запросов делается от конкретного клиента, не блокирую запросы остальных;
     * для синхронизации используем конструкцию synchronized, передавая в нее в каестве обьекта id клиента
     * который получаем из стандартного пула строк;
     *
     */
    public void example3(String clientId, String requestId){
        log.info("Начинаем обработку запроса requestId={} clientId={}", requestId, clientId);
        Client client = clientRepository.findByClientId(clientId);
        if(client == null){
            synchronized (clientId.intern()){
                client = clientRepository.findByClientId(clientId);
                if(client == null){
                    log.info("Создаем клиента: requestId={} clientId={}", requestId, requestId);
                    client = clientRepository.save(new Client(clientId));
                    log.info("Клиент успешно создан: requestId={} client={}", requestId, client);
                }
            }
        }
        log.info("Закончена обработка запроса requestId={} client={}", requestId, client);
    }

    /**
     *
     * Пример 3. Синхронизации по clientId. Вариант 2.
     *
     * в данном примере синхронизация запросов делается от конкретного клиента, не блокирую запросы остальных;
     * для синхронизации используем ReentrantLock, получаемые из пула которым вычтупает ConcurrentHashMap;
     *
     */
    public void example4(String clientId, String requestId){
        log.info("Начинаем обработку запроса requestId={} clientId={}", requestId, clientId);
        Client client = clientRepository.findByClientId(clientId);
        if(client == null){
            ReentrantLock lock = locks.computeIfAbsent(clientId, (k) -> new ReentrantLock());
            lock.lock();
            try{
                client = clientRepository.findByClientId(clientId);
                if(client == null){
                    log.info("Создаем клиента: requestId={} clientId={}", requestId, requestId);
                    client = clientRepository.save(new Client(clientId));
                    log.info("Клиент успешно создан: requestId={} client={}", requestId, client);
                }
            } finally {
                lock.unlock();
            }
        }
        log.info("Закончена обработка запроса requestId={} client={}", requestId, client);
    }

    /**
     *
     * Пример 3. Синхронизации по clientId. Вариант 3.
     *
     * в данном примере синхронизация запросов делается от конкретного клиента, не блокирую запросы остальных
     * для синхронизации используем Redisson, в кчачестве пула он использует redis;
     *
     */
    public void example5(String clientId, String requestId){
        log.info("Начинаем обработку запроса requestId={} clientId={}", requestId, clientId);
        Client client = clientRepository.findByClientId(clientId);
        if(client == null){
            RLock lock = redissonClient.getFairLock(clientId);
            lock.lock();
            try{
                client = clientRepository.findByClientId(clientId);
                if(client == null){
                    log.info("Создаем клиента: requestId={} clientId={}", requestId, requestId);
                    client = clientRepository.save(new Client(clientId));
                    log.info("Клиент успешно создан: requestId={} client={}", requestId, client);
                }
            } finally {
                lock.unlock();
            }
        }
        log.info("Закончена обработка запроса requestId={} client={}", requestId, client);
    }
}
