package com.todo.desktop.sync.job;

import com.todo.desktop.domain.usecase.DeadlineService;
import com.todo.desktop.domain.usecase.TaskService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

public final class DeadlineSyncJob implements Job {

    private final DeadlineService deadlineService;
    private final TaskService taskService;

    public DeadlineSyncJob(DeadlineService deadlineService, TaskService taskService) {
        this.deadlineService = deadlineService;
        this.taskService = taskService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        deadlineService.listDeadlines();
        taskService.listTasks();
    }
}
