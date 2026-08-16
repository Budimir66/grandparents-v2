package org.grandparents.statemachine;

/**
 * Состояния диалога при создании заявки и регистрации
 */
public enum DialogState {

    /**
     * Начальное состояние — бот ждёт команду
     */
    START,

    /**
     * Ожидание ввода имени подопечного
     */
    AWAITING_ELDER_NAME,

    /**
     * Ожидание ввода возраста
     */
    AWAITING_ELDER_AGE,

    /**
     * Ожидание ввода состояния здоровья
     */
    AWAITING_ELDER_HEALTH,

    /**
     * Ожидание ввода бюджета
     */
    AWAITING_ELDER_BUDGET,

    /**
     * Ожидание ввода локации
     */
    AWAITING_ELDER_LOCATION,

    /**
     * Ожидание ввода особых пожеланий
     */
    AWAITING_ELDER_REQUIREMENTS,

    /**
     * Ожидание названия пансионата (при регистрации оператора)
     */
    AWAITING_CAREHOME_NAME,
    AWAITING_CAREHOME_ADDRESS,
    AWAITING_CAREHOME_PHONE,
    AWAITING_CAREHOME_PRICE,

    /**
     * Анкета заполнена, заявка создаётся
     */
    COMPLETED,

    AWAITING_ELDER_PHONE,          // Ожидание выбора способа ввода телефона
    AWAITING_ELDER_PHONE_MANUAL,   // Ожидание ручного ввода телефона
    EDITING_ELDER,  // Режим редактирования заявки
    EDITING_ELDER_NAME,
    EDITING_ELDER_AGE,
    EDITING_ELDER_HEALTH,
    EDITING_ELDER_BUDGET,
    EDITING_ELDER_LOCATION,
    EDITING_ELDER_PHONE,
    EDITING_ELDER_REQUIREMENTS,
    EDITING_ELDER_CONFIRM,// Подтверждение сохранения
    AWAITING_CAREHOME_DESCRIPTION,
    AWAITING_CAREHOME_SPECIALIZATION,
    AWAITING_CAREHOME_OFFER_ACCEPT,
    EDITING_CAREHOME_NAME,
    EDITING_CAREHOME_ADDRESS,
    EDITING_CAREHOME_PHONE,
    EDITING_CAREHOME_PRICE,
    EDITING_CAREHOME_DESCRIPTION,
    EDITING_CAREHOME_SPECIALIZATION,
    EDITING_CAREHOME_CONFIRM,
    EDITING_CAREHOME_PHOTO,
    AWAITING_OPERATOR_ID,
    AWAITING_OPERATOR_NAME,
    AWAITING_OPERATOR_PHONE,
    AWAITING_OPERATOR_CAREHOME_NAME,
    VIEWING_ELDER,
    CONFIRM_DELETE_ELDER,
    AWAITING_CONTACT_FROM_MAX,// Ожидание контакта из MAX// Подтверждение удаления заявки// Просмотр карточки заявки
    AWAITING_ELDER_FINAL_COMMENTS,  // ← Финальные пожелания (последний шаг)
    AWAITING_CLIENT_NAME,  // Имя клиента (для операторов)
    AWAITING_MY_REQUESTS_CHOICE,  // Выбор раздела в "Мои заявки"
    AWAITING_COMPLETION_CONFIRMATION,  // Ожидание подтверждения закрытия заявки
    AWAITING_EXTEND_BUDGET,  // Ожидание нового бюджета для продления
    AWAITING_SETTINGS_CITY,       // Город
    AWAITING_SETTINGS_REGION,     // Регион
    AWAITING_SETTINGS_BUDGET_MIN, // Бюджет от
    AWAITING_SETTINGS_BUDGET_MAX, // Бюджет до
    AWAITING_SETTINGS_TIME_FROM,  // Время с
    AWAITING_SETTINGS_TIME_TO,    // Время до
    AWAITING_ADMIN_OPERATOR_ID,
    AWAITING_ADMIN_OPERATOR_ID_UNBLOCK,
    AWAITING_ADMIN_CAREHOME_NAME,
    AWAITING_CAREHOME_PROPOSAL,      // Начало предложения пансионата
    AWAITING_CAREHOME_NAME_PROPOSAL,
    AWAITING_CAREHOME_ADDRESS_PROPOSAL,
    AWAITING_CAREHOME_PHONE_PROPOSAL,
    AWAITING_CAREHOME_PRICE_PROPOSAL,
    AWAITING_CAREHOME_DESCRIPTION_PROPOSAL,
    AWAITING_CAREHOME_SPECIALIZATION_PROPOSAL,
    AWAITING_REJECT_COMMENT,  // Ожидание комментария при отклонении пансионата
    ADMIN_ADD_CAREHOME_NAME,
    ADMIN_ADD_CAREHOME_ADDRESS,
    ADMIN_ADD_CAREHOME_PHONE,
    ADMIN_ADD_CAREHOME_PRICE,
    ADMIN_ADD_CAREHOME_DESCRIPTION,
    ADMIN_ADD_CAREHOME_SPECIALIZATION,
    ADMIN_EDIT_CAREHOME_NAME,
    ADMIN_EDIT_CAREHOME_ADDRESS,
    ADMIN_EDIT_CAREHOME_PHONE,
    ADMIN_EDIT_CAREHOME_PRICE,
    ADMIN_EDIT_CAREHOME_DESCRIPTION,
    ADMIN_EDIT_CAREHOME_SPECIALIZATION,
    ADMIN_EDIT_CAREHOME_CONFIRM,
    ADMIN_CONFIRM_DELETE_CAREHOME,  // Подтверждение удаления пансионата
    ADMIN_EDIT_CAREHOME_LATITUDE,
    ADMIN_EDIT_CAREHOME_LONGITUDE,
    AWAITING_BONUS_NEW_VALUE,  // Ожидание нового значения бонуса
    AWAITING_CAREHOME_WEBSITE_PROPOSAL,
    ADMIN_ADD_CAREHOME_WEBSITE,  // Сайт пансионата (админ)// Сайт пансионата (предложение)
    ADMIN_EDIT_CAREHOME_WEBSITE,
    AWAITING_REJECT_COMMENT_FOR_ELDER,  // Ожидание комментария при отклонении заявки
    AWAITING_OFFER_ACCEPT,  // Ожидание принятия оферты
    EDITING_OPERATOR_NAME,   // Редактирование имени оператора
    EDITING_OPERATOR_PHONE,  // Редактирование телефона оператора
    EDITING_PROFILE_NAME,
    EDITING_PROFILE_PHONE,
    EDITING_PROFILE_WHATSAPP,
    EDITING_PROFILE_TELEGRAM,
    EDITING_PROFILE_EMAIL,
    AWAITING_CONSENT,  // Ожидание согласия на обработку данных
    AWAITING_CONSENT_BEFORE_FORM,  // Согласие перед заполнением анкеты

}

