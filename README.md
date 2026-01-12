# mangatracker
A full-stack manga tracking web app (React + Spring Boot) with MangaDex/MAL search, filters, and personal library.

More info: 
MangaTracker is a full-stack web app for discovering and tracking manga/manhwa/manhua. It includes browse + search, advanced tag filters (include/exclude + content warnings), author support, a “random with filters” discovery button, and a personal library. Built with React on the frontend and Spring Boot + H2 on the backend, with a pluggable provider architecture for integrating multiple metadata sources (MangaDex + MyAnimeList).

Latest update (1/12/26):
Currently the code is tailored & scalable to support one website (which is MangaDex) and currently gets all the information. Right now, the focus is to make my code simpler & make duplicate codes when supporting new websites into a similar classes so inherit saves time & allows different, etc. The next website to try scraping will be MyAnimeList.
