package by.bsuir.coursework.server.service;

/** Агрегированный этап исполнения заказа по связанным поставкам. */
public enum OrderRiskStage {
    /** Нет поставок или все в статусе PLANNED (начальный этап). */
    INITIAL,
    /** Есть активное движение (IN_TRANSIT и т.п.), без полного завершения. */
    IN_PROGRESS,
    /** Есть проблемные поставки DELAYED. */
    ISSUES_DELAYED,
    /** Все поставки DELIVERED. */
    COMPLETED
}
