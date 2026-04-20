CREATE TABLE public.users
(
    id         bigserial    NOT NULL,
    username   varchar(255) NOT NULL,
    "password" varchar(255) NOT NULL,
    full_name  varchar(255) NULL,
    provider   varchar(255) NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_username_key UNIQUE (username)
);
CREATE TABLE public.roles
(
    id     bigserial    NOT NULL,
    "name" varchar(255) NOT NULL,
    CONSTRAINT roles_name_key UNIQUE (name),
    CONSTRAINT roles_pkey PRIMARY KEY (id)
);

CREATE TABLE public.user_roles
(
    user_id int8 NOT NULL,
    role_id int8 NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles (id) ON DELETE CASCADE,
    CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE
);

INSERT INTO public.roles
    (id, "name")
VALUES (1, 'ROLE_ADMIN'),
       (2, 'ROLE_USER');