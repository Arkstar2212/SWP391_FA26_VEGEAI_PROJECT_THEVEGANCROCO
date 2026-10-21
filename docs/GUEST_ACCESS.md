# Guest access policy

Guest navigation contains exactly four business areas:

| Area | Frontend | Backend |
| --- | --- | --- |
| Recipe discovery and details | `/`, `/recipe/:id` | Recipe search, tags and public recipe details |
| Published Blog | `/blog`, `/blog/:id` | Published posts only |
| Approved questions and answers | `/questions`, `/questions/:id` | Approved questions only |
| Nearby vegetarian places | `/restaurants` | Place categories and search |

Registration, email verification, login and password recovery remain accessible
as account entry points. Swagger is developer documentation, not a Guest feature.

AI Chef, fridge scanning/inventory, nutrition calculation/profile, meal plans,
dashboard and video pages require login. Guest API requests receive JSON HTTP 401,
not an HTTP Basic login dialog. Guest AI trials are no longer enabled in the
security chain; the former quota implementation is retained but not registered.

Public recipe details remain readable but do not return video URLs for anonymous
requests, through either ID or slug. Signed-in Customer/Admin requests retain
published cooking videos. Frontend hides restricted navigation and promotional
links and redirects direct restricted URLs to login, retaining a safe return path.

Guest reads approved Q&A, but submitting a question requires Customer access.
Writing Blog, comments and favorites still follow existing authenticated roles.
Admin remains separate from Customer personal tools and cannot create questions.
The backend enum `MEMBER` currently represents the Customer actor; this policy
does not rename persisted roles or invalidate existing account records.

Tests cover the Guest public/protected API matrix, authenticated feature behavior,
absence of anonymous video links, and direct route protection. Existing profile,
nutrition, recipe, video and meal-plan records are not modified by this change.
