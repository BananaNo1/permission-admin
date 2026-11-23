create table wf_template (
    business varchar(64) not null references wf_business(code),
    code varchar(48) not null,
    engine_key varchar(64) not null unique,
    name varchar(100) not null,
    steps text not null,
    revision integer not null,
    published_version integer not null default 0,
    published_revision integer not null default 0,
    edited_by bigint not null,
    edited_at timestamp with time zone not null default current_timestamp,
    primary key (business, code)
);
create table wf_template_release (
    business varchar(64) not null,
    code varchar(48) not null,
    version integer not null,
    name varchar(100) not null,
    steps text not null,
    definition_id varchar(128) not null unique,
    published_by bigint not null,
    published_at timestamp with time zone not null default current_timestamp,
    primary key (business, code, version),
    foreign key (business, code) references wf_template(business, code)
);
create table wf_template_instance (
    business varchar(64) not null,
    code varchar(48) not null,
    business_key varchar(120) not null,
    version integer not null,
    instance_id varchar(128) not null unique,
    primary key (business, code, business_key),
    foreign key (business, code, version) references wf_template_release(business, code, version)
);

