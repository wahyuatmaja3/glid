package com.glid.service;

import com.glid.model.Employee;
import com.glid.persistence.AppDataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EmployeeService {
    private final AppDataStore store;

    public EmployeeService(AppDataStore store) {
        this.store = store;
    }

    public List<Employee> findAll() {
        return store.findAllEmployees();
    }

    public Employee registerEmployee(String employeeCode, String fullName, String department, String position, boolean active, boolean maskRegistered) {
        Employee employee = store.saveEmployee(employeeCode, fullName, department, position, active, maskRegistered);
        for (int i = 0; i < 5; i++) {
            store.saveEmbedding(employee.id(), generateEmbeddingSeed(), maskRegistered && i % 2 == 0);
        }
        return employee;
    }

    public Employee findById(long employeeId) {
        return store.findEmployeeById(employeeId);
    }

    private List<Double> generateEmbeddingSeed() {
        List<Double> values = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            values.add(Math.round(random.nextDouble(0.05, 0.95) * 100.0) / 100.0);
        }
        return values;
    }
}
