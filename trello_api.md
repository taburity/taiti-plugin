# Documentação da API do Trello

## Visão Geral

A API do Trello é uma API RESTful que permite aos desenvolvedores interagir programaticamente com os recursos do Trello, como quadros, listas, cartões, membros e organizações. Esta documentação fornece uma visão geral dos principais endpoints e instruções para começar a usar a API. Para detalhes completos, consulte a [documentação oficial da API do Trello](https://developer.atlassian.com/cloud/trello/rest/).

## Autenticação

Para usar a API do Trello, é necessário autenticar todas as solicitações com uma chave de API e um token. Siga estas etapas para obtê-los:

1. **Crie um Power-Up**: Acesse o [Portal de Administração de Power-Ups](https://trello.com/power-ups/admin) e crie um novo Power-Up.
2. **Gere a Chave de API**: No painel do Power-Up, vá para a aba "Chave de API" e clique em "Gerar uma nova chave de API".
3. **Gere o Token**: Na mesma página, clique no link "Token" para gerar um token de usuário. O token deve ser mantido em segredo, pois concede acesso aos dados do usuário.

Inclua a chave e o token nos parâmetros de consulta de cada solicitação:

```
https://api.trello.com/1/boards/{id}?key=SUA_CHAVE&token=SEU_TOKEN
```

**Nota**: A chave de API é pública, mas o token é sensível e deve ser protegido.

## Recursos

A API do Trello organiza seus endpoints em torno de recursos principais. Abaixo, listamos os recursos mais comuns, seus endpoints principais e uma breve descrição. Cada endpoint requer os parâmetros `key` e `token` na query string.

### Quadros (Boards)

Os quadros são o espaço principal onde o trabalho é organizado no Trello. Eles contêm listas e cartões.

| Endpoint                | Método | Descrição                          | Parâmetros Principais (Exemplo) |
|-------------------------|--------|------------------------------------|-------------------------------|
| `/boards/{id}`          | GET    | Recupera um quadro pelo ID         | `fields`                     |
| `/boards`               | POST   | Cria um novo quadro                | `name`, `desc`, `idOrganization` |
| `/boards/{id}`          | PUT    | Atualiza um quadro existente       | `name`, `desc`, `closed`     |
| `/boards/{id}`          | DELETE | Exclui um quadro                   | -                            |

### Cartões (Cards)

Os cartões representam tarefas ou itens de trabalho em um quadro.

| Endpoint                | Método | Descrição                          | Parâmetros Principais (Exemplo) |
|-------------------------|--------|------------------------------------|-------------------------------|
| `/cards/{id}`           | GET    | Recupera um cartão pelo ID         | `fields`, `attachments`      |
| `/cards`                | POST   | Cria um novo cartão                | `idList` (obrigatório), `name`, `desc` |
| `/cards/{id}`           | PUT    | Atualiza um cartão existente       | `name`, `desc`, `idList`     |
| `/cards/{id}`           | DELETE | Exclui um cartão                   | -                            |

### Listas (Lists)

As listas organizam cartões dentro de um quadro.

| Endpoint                | Método | Descrição                          | Parâmetros Principais (Exemplo) |
|-------------------------|--------|------------------------------------|-------------------------------|
| `/lists/{id}`           | GET    | Recupera uma lista pelo ID         | `fields`                     |
| `/lists`                | POST   | Cria uma nova lista                | `idBoard` (obrigatório), `name`, `pos` |
| `/lists/{id}`           | PUT    | Atualiza uma lista existente       | `name`, `pos`, `closed`      |
| `/lists/{id}/closed`    | PUT    | Arquiva uma lista                  | `value` (true/false)         |

### Membros (Members)

Os membros são usuários que interagem com quadros e cartões.

| Endpoint                | Método | Descrição                          | Parâmetros Principais (Exemplo) |
|-------------------------|--------|------------------------------------|-------------------------------|
| `/members/{id}`         | GET    | Recupera um membro pelo ID         | `fields`, `boards`           |
| `/members/{id}/boards`  | GET    | Recupera os quadros de um membro   | `filter`, `fields`           |

### Organizações (Organizations)

As organizações agrupam quadros e membros, geralmente para equipes ou empresas.

| Endpoint                | Método | Descrição                          | Parâmetros Principais (Exemplo) |
|-------------------------|--------|------------------------------------|-------------------------------|
| `/organizations/{id}`   | GET    | Recupera uma organização pelo ID   | `fields`, `members`          |
| `/organizations`        | POST   | Cria uma nova organização          | `displayName` (obrigatório), `desc` |
| `/organizations/{id}`   | PUT    | Atualiza uma organização existente | `displayName`, `desc`        |
| `/organizations/{id}`   | DELETE | Exclui uma organização             | -                            |

### Outros Recursos

A API do Trello suporta recursos adicionais, incluindo:

- **Ações (Actions)**: Gerencia ações realizadas em recursos, como comentários ou movimentações de cartões.
- **Listas de Verificação (Checklists)**: Gerencia listas de verificação em cartões.
- **Rótulos (Labels)**: Gerencia rótulos aplicados a cartões.
- **Notificações (Notifications)**: Recupera notificações de atividades.
- **Pesquisa (Search)**: Realiza buscas em recursos como quadros, cartões e membros.
- **Tokens**: Gerencia tokens de API.
- **Webhooks**: Configura notificações em tempo real para eventos específicos.

Para endpoints específicos desses recursos, consulte a [documentação oficial](https://developer.atlassian.com/cloud/trello/rest/).

## Limites de Taxa

O Trello impõe limites de taxa para evitar sobrecarga nos servidores. Atualmente, o limite é de 300 solicitações por 10 segundos por chave de API e 100 solicitações por 10 segundos por token. Se exceder, você receberá um erro 429. Para mais detalhes, consulte a [documentação de limites de taxa](https://developer.atlassian.com/cloud/trello/guides/rest-api/rate-limits/).

## Webhooks

Os webhooks permitem receber notificações em tempo real quando ocorrem eventos no Trello, como a criação de um cartão ou a atualização de um quadro. Não há limite para o número de webhooks que você pode configurar. Para mais informações, consulte a [documentação de webhooks](https://developer.atlassian.com/cloud/trello/guides/rest-api/webhooks/).

## Códigos de Erro Comuns

| Código | Descrição                              |
|--------|----------------------------------------|
| 400    | Solicitação mal formatada ou faltando informações |
| 401    | Não autorizado; permissões insuficientes |
| 403    | Proibido; a solicitação não é permitida |
| 404    | Recurso não encontrado                 |
| 429    | Excedeu o limite de taxa              |
| 503    | Servidor indisponível                 |
| 504    | Tempo limite do gateway               |

## Notas Adicionais

- **Formato de Resposta**: Todas as respostas da API são em JSON.
- **Versão da API**: A versão atual é `/1`, incluída na URL (ex.: `https://api.trello.com/1/`).
- **Client.js**: Para aplicativos web, o Trello oferece uma biblioteca JavaScript (`client.js`) para facilitar a autorização e as solicitações. Veja mais em [documentação de client.js](https://developer.atlassian.com/cloud/trello/guides/rest-api/client-js/).

Para uma lista completa de endpoints, parâmetros e exemplos, visite a [documentação oficial da API do Trello](https://developer.atlassian.com/cloud/trello/rest/).