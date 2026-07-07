# Decisões de arquitetura

Este documento reúne as escolhas técnicas do backend que valem a pena saber
explicar — o "porquê", não só o "o quê". A documentação completa de domínio
de negócio, casos de uso e modelo de dados fica no Notion do projeto; aqui
ficam apenas as decisões de implementação.

## Timestamps controlados pelo banco, não pelo Java

As colunas `created_at`/`updated_at` são mapeadas como
`insertable = false, updatable = false` nas entidades JPA. O valor real vem
do `DEFAULT now()` e do trigger `set_updated_at()` do PostgreSQL, definidos
no schema (`db/flowops_ddl.sql`) — nunca de lógica duplicada na aplicação.
Isso evita divergência entre o horário do banco e o do servidor de aplicação,
e garante que qualquer UPDATE feito fora da aplicação (uma migração manual,
por exemplo) também atualiza `updated_at` corretamente.

## UUID gerado no Java, não no banco

Embora a coluna tenha `DEFAULT gen_random_uuid()`, o `@PrePersist` gera o
UUID antes do insert, para que o objeto já tenha o valor correto em memória
sem precisar de um `refresh()` depois de salvar.

## IDs internos nunca são expostos na API

O `id` sequencial (`BIGSERIAL`) é usado apenas internamente, para joins e
índices. Toda resposta da API e toda rota que recebe um identificador do
cliente HTTP usa o `uuid` — não expor sequências previsíveis é uma prática
básica de segurança, e vale mais a pena aplicá-la desde o primeiro módulo do
que corrigir depois.

## JWT stateless com claims mínimos

O payload carrega apenas `userId`, `companyId` e `role` — o suficiente para
autorização, sem inflar o token com dados que mudam com frequência (isso
ficaria desatualizado até o token expirar, já que o token não é validado
contra o banco a cada requisição).

## UserDetails implementado diretamente na entidade User

Evita uma classe adaptadora extra. Para o tamanho atual do domínio, o
acoplamento entre entidade JPA e o contrato do Spring Security é aceitável;
a alternativa (um DTO de segurança separado, hidratado a partir do usuário)
vale a pena revisitar se o modelo de usuário crescer muito.

## `ddl-auto: validate`, nunca `update` ou `create`

O schema é responsabilidade exclusiva do `flowops_ddl.sql`. O Hibernate
apenas confirma que o mapeamento das entidades bate com as tabelas
existentes — se alguém alterar uma entidade sem atualizar o DDL, a aplicação
falha ao iniciar, em vez de alterar o banco silenciosamente em produção.

## Carregamento de relacionamentos: LAZY por padrão, EntityGraph quando necessário

Todo `@ManyToOne` é `LAZY` por padrão — evita carregar dados que a maioria
das consultas não precisa. Onde um caminho de código específico precisa do
relacionamento carregado fora de uma transação (como o filtro de
autenticação JWT, que roda antes de qualquer `@Transactional`), a solução é
um `@EntityGraph` no método do repositório usado por aquele caminho, em vez
de tornar o relacionamento `EAGER` globalmente — isso evita penalizar todas
as outras consultas que não precisam do dado extra.

## Exclusão inteligente: hard delete quando possível, soft delete quando necessário

`ClientService.deactivate` decide entre remover um cliente de verdade
(`DELETE`) ou apenas desativá-lo (`deleted_at`), com base em uma checagem
explícita (`WorkOrderRepository.existsByClientId`) — nunca tentando o
`DELETE` e reagindo a uma exceção de violação de FK como controle de fluxo.

Isso importa por um motivo concreto, não só estético: no PostgreSQL, uma
transação que sofre qualquer erro fica **abortada por completo** até o fim
do bloco — uma tentativa de `DELETE` que falha por violação de FK impede
qualquer comando seguinte na mesma transação, incluindo o `UPDATE` de soft
delete que tentaria vir logo depois. Ou seja, "tenta deletar, se der erro
faz soft delete" não funciona dentro de uma única transação sem uma segunda
transação isolada — e o `existsBy` explícito evita essa complicação
inteiramente, decidindo antes de qualquer escrita no banco.

A constraint de FK (`work_orders.client_id REFERENCES clients(id)`)
continua existindo como rede de segurança: se uma violação chegar mesmo
assim (uma tabela nova referenciando `clients` sem checagem explícita
atualizada, por exemplo), o `GlobalExceptionHandler` responde com 409 em
vez de deixar vazar um 500.

## Email de usuário é único globalmente, não por empresa

A constraint original era `UNIQUE(company_id, email)`, permitindo o mesmo
e-mail em empresas diferentes. Isso quebrava o login: `UserRepository.
findByEmailAndActiveTrue` espera uma única linha, e com dois usuários ativos
compartilhando e-mail em empresas diferentes a consulta retornava duas,
lançando `IncorrectResultSizeDataAccessException` (500).

A correção (`UNIQUE(email)` global) resolve o problema para o modelo atual,
onde cada usuário pertence a exatamente uma empresa. Ver
[`docs/adr/0001-modelo-de-usuario.md`](adr/0001-modelo-de-usuario.md) para a
decisão consciente de manter esse modelo e não migrar para uma identidade de
usuário global neste momento do projeto.

## Isolamento multi-tenant reforçado em toda consulta por identificador

Nenhuma consulta que recebe um identificador vindo de fora usa `findById()`
puro. Toda busca por um registro específico (cliente, e futuramente
WorkOrder, etc.) filtra também por `company_id` do usuário autenticado —
sem isso, bastaria adivinhar ou enumerar um UUID para ler ou alterar dados
de outra empresa.

