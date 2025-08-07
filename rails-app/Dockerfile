FROM ruby:3.1.2

WORKDIR /app

COPY Gemfile Gemfile.lock ./
RUN bundle install

COPY entrypoint.sh /usr/bin/
RUN chmod +x /usr/bin/entrypoint.sh
ENTRYPOINT ["entrypoint.sh"]
EXPOSE 3000

# CMD ["rails", "server", "-b", "0.0.0.0"]
CMD ["bundle", "exec", "foreman", "start"]
